package ru.spb.reshenie.chekerstatus.security.repository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import ru.spb.reshenie.chekerstatus.security.model.AccessPermission;
import ru.spb.reshenie.chekerstatus.security.model.AccessRole;
import ru.spb.reshenie.chekerstatus.security.model.ManagedUser;
import ru.spb.reshenie.chekerstatus.security.model.SecurityUserAccount;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class AccessControlRepository {

    private final JdbcTemplate jdbcTemplate;
    private final Logger log = LoggerFactory.getLogger(getClass());

    public AccessControlRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public SecurityUserAccount findSecurityUserByUsername(String username) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id, username, password_hash, enabled " +
                            "FROM app_user WHERE lower(username) = lower(?)",
                    (rs, rowNum) -> new SecurityUserAccount(
                            rs.getLong("id"),
                            rs.getString("username"),
                            rs.getString("password_hash"),
                            rs.getBoolean("enabled")
                    ),
                    username
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public ManagedUser findManagedUser(long userId) {
        List<ManagedUser> users = loadManagedUsers(
                "SELECT id, username, display_name, enabled FROM app_user WHERE id = ? ORDER BY lower(username), id",
                userId
        );
        return users.isEmpty() ? null : users.getFirst();
    }

    public List<String> findAuthorityCodesByUserId(long userId) {
        String sql = """
                     with au as (select *
                                   from app_user au
                                  where au.id = ?)
                     select authority
                       from (select CONCAT('ROLE_', r.code) as authority
                               from au
                               join app_user_role ur
                                 on ur.user_id = au.id
                               join app_role r
                                 on r.id = ur.role_id
                             union
                             select p.code as authority
                               from au
                               join app_user_permission up
                                 on up.user_id = au.id
                               join app_permission p
                                 on p.id = up.permission_id
                             union
                             select p.code as authority
                               from au
                               join app_user_role ur
                                 on ur.user_id = au.id
                               join app_role_permission rp
                                 on rp.role_id = ur.role_id
                               join app_permission p
                                 on p.id = rp.permission_id) authorities
                     order by authority
                     """;
        log.info(sql);
        return jdbcTemplate.queryForList(
                sql,
                String.class,
                userId
        );
    }

    public List<ManagedUser> findAllManagedUsers() {
        return loadManagedUsers("SELECT id, username, display_name, enabled FROM app_user ORDER BY lower(username), id");
    }

    public List<AccessRole> findAllRoles() {
        List<RoleRow> rows = jdbcTemplate.query(
                "SELECT r.id, r.code, r.name, r.description, p.code AS permission_code " +
                        "FROM app_role r " +
                        "LEFT JOIN app_role_permission rp ON rp.role_id = r.id " +
                        "LEFT JOIN app_permission p ON p.id = rp.permission_id " +
                        "ORDER BY r.name, p.name, p.code",
                (rs, rowNum) -> new RoleRow(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("description"),
                        rs.getString("permission_code")
                )
        );
        Map<String, RoleBuilder> builders = new LinkedHashMap<String, RoleBuilder>();
        for (RoleRow row : rows) {
            RoleBuilder builder = builders.get(row.code);
            if (builder == null) {
                builder = new RoleBuilder(row.id, row.code, row.name, row.description);
                builders.put(row.code, builder);
            }
            if (row.permissionCode != null) {
                builder.permissionCodes.add(row.permissionCode);
            }
        }
        List<AccessRole> roles = new ArrayList<AccessRole>();
        for (RoleBuilder builder : builders.values()) {
            roles.add(builder.build());
        }
        return roles;
    }

    public List<AccessPermission> findAllPermissions() {
        return jdbcTemplate.query(
                "SELECT id, code, name, description FROM app_permission ORDER BY name, code",
                (rs, rowNum) -> new AccessPermission(
                        rs.getLong("id"),
                        rs.getString("code"),
                        rs.getString("name"),
                        rs.getString("description")
                )
        );
    }

    public Long findUserIdByUsername(String username) {
        try {
            return jdbcTemplate.queryForObject(
                    "SELECT id FROM app_user WHERE lower(username) = lower(?)",
                    Long.class,
                    username
            );
        } catch (EmptyResultDataAccessException e) {
            return null;
        }
    }

    public long insertUser(String username, String displayName, String passwordHash, boolean enabled) {
        Long id = jdbcTemplate.queryForObject(
                "INSERT INTO app_user (username, display_name, password_hash, enabled, created_at, updated_at) " +
                        "VALUES (?, ?, ?, ?, now(), now()) RETURNING id",
                Long.class,
                username,
                displayName,
                passwordHash,
                Boolean.valueOf(enabled)
        );
        return id == null ? 0L : id.longValue();
    }

    public void updateUser(long userId, String username, String displayName, boolean enabled) {
        jdbcTemplate.update(
                "UPDATE app_user SET username = ?, display_name = ?, enabled = ?, updated_at = now() WHERE id = ?",
                username,
                displayName,
                Boolean.valueOf(enabled),
                Long.valueOf(userId)
        );
    }

    public void updateUserEnabled(long userId, boolean enabled) {
        jdbcTemplate.update(
                "UPDATE app_user SET enabled = ?, updated_at = now() WHERE id = ?",
                Boolean.valueOf(enabled),
                Long.valueOf(userId)
        );
    }

    public void updateUserPassword(long userId, String passwordHash) {
        jdbcTemplate.update(
                "UPDATE app_user SET password_hash = ?, updated_at = now() WHERE id = ?",
                passwordHash,
                Long.valueOf(userId)
        );
    }

    public void replaceUserRoles(long userId, Set<String> roleCodes) {
        jdbcTemplate.update("DELETE FROM app_user_role WHERE user_id = ?", Long.valueOf(userId));
        for (String roleCode : roleCodes) {
            jdbcTemplate.update(
                    "INSERT INTO app_user_role (user_id, role_id) " +
                            "SELECT ?, id FROM app_role WHERE code = ?",
                    Long.valueOf(userId),
                    roleCode
            );
        }
    }

    public void replaceUserPermissions(long userId, Set<String> permissionCodes) {
        jdbcTemplate.update("DELETE FROM app_user_permission WHERE user_id = ?", Long.valueOf(userId));
        for (String permissionCode : permissionCodes) {
            jdbcTemplate.update(
                    "INSERT INTO app_user_permission (user_id, permission_id) " +
                            "SELECT ?, id FROM app_permission WHERE code = ?",
                    Long.valueOf(userId),
                    permissionCode
            );
        }
    }

    public void ensureRoleAssigned(long userId, String roleCode) {
        jdbcTemplate.update(
                "INSERT INTO app_user_role (user_id, role_id) " +
                        "SELECT ?, id FROM app_role WHERE code = ? " +
                        "ON CONFLICT DO NOTHING",
                Long.valueOf(userId),
                roleCode
        );
    }

    public long countEnabledUsersWithPermission(String permissionCode, Long excludedUserId) {
        String sql = "SELECT count(*) FROM (" +
                "SELECT DISTINCT u.id " +
                "FROM app_user u " +
                "JOIN app_user_permission up ON up.user_id = u.id " +
                "JOIN app_permission p ON p.id = up.permission_id " +
                "WHERE u.enabled = true AND p.code = ? " +
                "UNION " +
                "SELECT DISTINCT u.id " +
                "FROM app_user u " +
                "JOIN app_user_role ur ON ur.user_id = u.id " +
                "JOIN app_role_permission rp ON rp.role_id = ur.role_id " +
                "JOIN app_permission p ON p.id = rp.permission_id " +
                "WHERE u.enabled = true AND p.code = ? " +
                ") granted";
        if (excludedUserId != null) {
            Long value = jdbcTemplate.queryForObject(
                    sql + " WHERE id <> ?",
                    Long.class,
                    permissionCode,
                    permissionCode,
                    excludedUserId
            );
            return value == null ? 0L : value.longValue();
        }
        Long value = jdbcTemplate.queryForObject(sql, Long.class, permissionCode, permissionCode);
        return value == null ? 0L : value.longValue();
    }

    private List<ManagedUser> loadManagedUsers(String sql, Object... args) {
        List<UserRow> rows = jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new UserRow(
                        rs.getLong("id"),
                        rs.getString("username"),
                        rs.getString("display_name"),
                        rs.getBoolean("enabled")
                ),
                args
        );
        if (rows.isEmpty()) {
            return List.of();
        }

        Map<Long, Set<String>> roleCodesByUserId = loadUserCodeMap(
                "SELECT ur.user_id, r.code " +
                        "FROM app_user_role ur " +
                        "JOIN app_role r ON r.id = ur.role_id " +
                        "ORDER BY ur.user_id, r.code"
        );
        Map<Long, Set<String>> directPermissionCodesByUserId = loadUserCodeMap(
                "SELECT up.user_id, p.code " +
                        "FROM app_user_permission up " +
                        "JOIN app_permission p ON p.id = up.permission_id " +
                        "ORDER BY up.user_id, p.code"
        );
        Map<Long, Set<String>> effectivePermissionCodesByUserId = loadUserCodeMap(
                "SELECT granted.user_id, granted.permission_code " +
                        "FROM (" +
                        "SELECT up.user_id, p.code AS permission_code " +
                        "FROM app_user_permission up " +
                        "JOIN app_permission p ON p.id = up.permission_id " +
                        "UNION " +
                        "SELECT ur.user_id, p.code AS permission_code " +
                        "FROM app_user_role ur " +
                        "JOIN app_role_permission rp ON rp.role_id = ur.role_id " +
                        "JOIN app_permission p ON p.id = rp.permission_id " +
                        ") granted " +
                        "ORDER BY granted.user_id, granted.permission_code"
        );

        List<ManagedUser> result = new ArrayList<ManagedUser>();
        for (UserRow row : rows) {
            result.add(new ManagedUser(
                    row.id,
                    row.username,
                    row.displayName,
                    row.enabled,
                    codesFor(roleCodesByUserId, row.id),
                    codesFor(directPermissionCodesByUserId, row.id),
                    codesFor(effectivePermissionCodesByUserId, row.id)
            ));
        }
        return result;
    }

    private Map<Long, Set<String>> loadUserCodeMap(String sql) {
        return jdbcTemplate.query(
                sql,
                rs -> {
                    Map<Long, Set<String>> result = new LinkedHashMap<Long, Set<String>>();
                    while (rs.next()) {
                        Long userId = Long.valueOf(rs.getLong(1));
                        String code = rs.getString(2);
                        Set<String> codes = result.get(userId);
                        if (codes == null) {
                            codes = new LinkedHashSet<String>();
                            result.put(userId, codes);
                        }
                        if (code != null) {
                            codes.add(code);
                        }
                    }
                    return result;
                }
        );
    }

    private Set<String> codesFor(Map<Long, Set<String>> codesByUserId, long userId) {
        Set<String> codes = codesByUserId.get(Long.valueOf(userId));
        return codes == null ? new LinkedHashSet<String>() : codes;
    }

    private static class UserRow {
        private final long id;
        private final String username;
        private final String displayName;
        private final boolean enabled;

        private UserRow(long id, String username, String displayName, boolean enabled) {
            this.id = id;
            this.username = username;
            this.displayName = displayName;
            this.enabled = enabled;
        }
    }

    private static class RoleRow {
        private final long id;
        private final String code;
        private final String name;
        private final String description;
        private final String permissionCode;

        private RoleRow(long id, String code, String name, String description, String permissionCode) {
            this.id = id;
            this.code = code;
            this.name = name;
            this.description = description;
            this.permissionCode = permissionCode;
        }
    }

    private static class RoleBuilder {
        private final long id;
        private final String code;
        private final String name;
        private final String description;
        private final Set<String> permissionCodes = new LinkedHashSet<String>();

        private RoleBuilder(long id, String code, String name, String description) {
            this.id = id;
            this.code = code;
            this.name = name;
            this.description = description;
        }

        private AccessRole build() {
            return new AccessRole(id, code, name, description, permissionCodes);
        }
    }
}
