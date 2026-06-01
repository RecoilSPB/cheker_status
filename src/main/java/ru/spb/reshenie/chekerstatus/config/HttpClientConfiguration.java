package ru.spb.reshenie.chekerstatus.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

@Configuration
public class HttpClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(HttpClientConfiguration.class);

    @Bean
    public RestTemplate restTemplate(NsiProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = properties.isTrustAllSsl()
                ? trustAllSslRequestFactory()
                : new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(15_000);
        requestFactory.setReadTimeout(60_000);
        return new RestTemplate(requestFactory);
    }

    private SimpleClientHttpRequestFactory trustAllSslRequestFactory() {
        log.warn("NSI HTTPS certificate validation is disabled by nsi.trust-all-ssl=true");
        final SSLSocketFactory sslSocketFactory = trustAllSslSocketFactory();
        final HostnameVerifier hostnameVerifier = new HostnameVerifier() {
            @Override
            public boolean verify(String hostname, SSLSession session) {
                return true;
            }
        };

        return new SimpleClientHttpRequestFactory() {
            @Override
            protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
                if (connection instanceof HttpsURLConnection) {
                    HttpsURLConnection httpsConnection = (HttpsURLConnection) connection;
                    httpsConnection.setSSLSocketFactory(sslSocketFactory);
                    httpsConnection.setHostnameVerifier(hostnameVerifier);
                }
                super.prepareConnection(connection, httpMethod);
            }
        };
    }

    private SSLSocketFactory trustAllSslSocketFactory() {
        try {
            TrustManager[] trustManagers = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, trustManagers, new SecureRandom());
            return sslContext.getSocketFactory();
        } catch (Exception e) {
            throw new IllegalStateException("Cannot initialize trust-all SSL context", e);
        }
    }
}
