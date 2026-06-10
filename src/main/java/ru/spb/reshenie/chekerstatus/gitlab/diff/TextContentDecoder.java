package ru.spb.reshenie.chekerstatus.gitlab.diff;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public final class TextContentDecoder {

    private static final Charset WINDOWS_1251 = Charset.forName("windows-1251");
    private static final Charset KOI8_R = Charset.forName("KOI8-R");
    private static final Charset IBM866 = Charset.forName("IBM866");

    private TextContentDecoder() {
    }

    public static String decode(byte[] bytes, String path) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }

        Bom bom = detectBom(bytes);
        if (bom != null) {
            return new String(bytes, bom.offset(), bytes.length - bom.offset(), bom.charset());
        }

        if (looksLikeUtf16Le(bytes)) {
            return new String(bytes, StandardCharsets.UTF_16LE);
        }
        if (looksLikeUtf16Be(bytes)) {
            return new String(bytes, StandardCharsets.UTF_16BE);
        }

        String utf8 = decodeStrict(bytes, StandardCharsets.UTF_8);
        if (utf8 != null) {
            return utf8;
        }
        if (containsCyrillic(path)) {
            String windows1251 = new String(bytes, WINDOWS_1251);
            if (looksReasonableCyrillicText(windows1251)) {
                return windows1251;
            }
        }

        return decodeBestEffort(bytes, path);
    }

    private static Bom detectBom(byte[] bytes) {
        if (bytes.length >= 3
                && (bytes[0] & 0xff) == 0xef
                && (bytes[1] & 0xff) == 0xbb
                && (bytes[2] & 0xff) == 0xbf) {
            return new Bom(StandardCharsets.UTF_8, 3);
        }
        if (bytes.length >= 2
                && (bytes[0] & 0xff) == 0xff
                && (bytes[1] & 0xff) == 0xfe) {
            return new Bom(StandardCharsets.UTF_16LE, 2);
        }
        if (bytes.length >= 2
                && (bytes[0] & 0xff) == 0xfe
                && (bytes[1] & 0xff) == 0xff) {
            return new Bom(StandardCharsets.UTF_16BE, 2);
        }
        return null;
    }

    private static boolean looksLikeUtf16Le(byte[] bytes) {
        return looksLikeUtf16(bytes, false);
    }

    private static boolean looksLikeUtf16Be(byte[] bytes) {
        return looksLikeUtf16(bytes, true);
    }

    private static boolean looksLikeUtf16(byte[] bytes, boolean evenNullsExpected) {
        if (bytes.length < 4) {
            return false;
        }
        int pairs = Math.min(bytes.length / 2, 128);
        int expectedNulls = 0;
        int unexpectedNulls = 0;
        for (int i = 0; i < pairs; i++) {
            int even = bytes[i * 2] & 0xff;
            int odd = bytes[i * 2 + 1] & 0xff;
            if (evenNullsExpected) {
                if (even == 0) {
                    expectedNulls++;
                }
                if (odd == 0) {
                    unexpectedNulls++;
                }
            } else {
                if (odd == 0) {
                    expectedNulls++;
                }
                if (even == 0) {
                    unexpectedNulls++;
                }
            }
        }
        return expectedNulls >= Math.max(2, pairs / 4) && unexpectedNulls <= Math.max(1, pairs / 20);
    }

    private static String decodeStrict(byte[] bytes, Charset charset) {
        CharsetDecoder decoder = charset.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            return decoder.decode(ByteBuffer.wrap(bytes)).toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }

    private static String decodeBestEffort(byte[] bytes, String path) {
        List<Charset> candidates = candidateCharsets(path);
        String bestText = new String(bytes, candidates.get(0));
        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < candidates.size(); i++) {
            Charset charset = candidates.get(i);
            String decoded = new String(bytes, charset);
            int score = score(decoded, path) - i;
            if (score > bestScore) {
                bestScore = score;
                bestText = decoded;
            }
        }
        return bestText;
    }

    private static List<Charset> candidateCharsets(String path) {
        List<Charset> candidates = new ArrayList<Charset>();
        if (containsCyrillic(path)) {
            candidates.add(WINDOWS_1251);
            candidates.add(KOI8_R);
            candidates.add(IBM866);
            candidates.add(StandardCharsets.ISO_8859_1);
            return candidates;
        }
        candidates.add(StandardCharsets.ISO_8859_1);
        candidates.add(WINDOWS_1251);
        candidates.add(KOI8_R);
        candidates.add(IBM866);
        return candidates;
    }

    private static int score(String text, String path) {
        boolean cyrillicHint = containsCyrillic(path);
        int score = 0;
        int cyrillicLetters = 0;
        int latinLetters = 0;
        int replacementChars = 0;
        int controlChars = 0;

        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\ufffd') {
                replacementChars++;
                continue;
            }
            if (isUnsafeControl(ch)) {
                controlChars++;
                continue;
            }
            if (Character.isLetter(ch)) {
                score += 2;
                if (isCyrillic(ch)) {
                    cyrillicLetters++;
                } else if (isLatin(ch)) {
                    latinLetters++;
                }
                continue;
            }
            if (Character.isDigit(ch)) {
                score += 1;
                continue;
            }
            if (Character.isWhitespace(ch) || isCommonTextPunctuation(ch)) {
                score += 1;
            }
        }

        score -= replacementChars * 100;
        score -= controlChars * 40;
        if (cyrillicHint) {
            score += cyrillicLetters * 3;
        } else if (latinLetters > 0) {
            score += latinLetters * 2;
        }
        if (cyrillicLetters > 0 && latinLetters == 0) {
            score += 10;
        }
        if (latinLetters > 0 && cyrillicLetters == 0) {
            score += 10;
        }
        return score;
    }

    private static boolean containsCyrillic(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (isCyrillic(value.charAt(i))) {
                return true;
            }
        }
        return false;
    }

    private static boolean isCyrillic(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.CYRILLIC
                || block == Character.UnicodeBlock.CYRILLIC_SUPPLEMENTARY
                || block == Character.UnicodeBlock.CYRILLIC_EXTENDED_A
                || block == Character.UnicodeBlock.CYRILLIC_EXTENDED_B;
    }

    private static boolean isLatin(char ch) {
        Character.UnicodeBlock block = Character.UnicodeBlock.of(ch);
        return block == Character.UnicodeBlock.BASIC_LATIN
                || block == Character.UnicodeBlock.LATIN_1_SUPPLEMENT
                || block == Character.UnicodeBlock.LATIN_EXTENDED_A
                || block == Character.UnicodeBlock.LATIN_EXTENDED_B;
    }

    private static boolean isUnsafeControl(char ch) {
        return Character.isISOControl(ch) && ch != '\n' && ch != '\r' && ch != '\t' && ch != '\f';
    }

    private static boolean isCommonTextPunctuation(char ch) {
        return ",.;:!?\"'()[]{}<>/-_+=*@#%&\\|".indexOf(ch) >= 0;
    }

    private static boolean looksReasonableCyrillicText(String text) {
        int cyrillicLetters = 0;
        int unsafeControls = 0;
        int replacementChars = 0;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '\ufffd') {
                replacementChars++;
            } else if (isUnsafeControl(ch)) {
                unsafeControls++;
            } else if (isCyrillic(ch)) {
                cyrillicLetters++;
            }
        }
        return replacementChars == 0 && unsafeControls == 0 && cyrillicLetters > 0;
    }

    private record Bom(Charset charset, int offset) {
    }
}
