CREATE TABLE app_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(120) NOT NULL,
    display_name VARCHAR(200),
    password_hash VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX ux_app_user_username_lower ON app_user ((lower(username)));

CREATE TABLE app_role (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(80) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000)
);

CREATE TABLE app_permission (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(120) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    description VARCHAR(1000)
);

CREATE TABLE app_user_role (
    user_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES app_role (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE app_role_permission (
    role_id BIGINT NOT NULL REFERENCES app_role (id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES app_permission (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE app_user_permission (
    user_id BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES app_permission (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, permission_id)
);

INSERT INTO app_permission (code, name, description)
VALUES ('dashboard.view', 'Просмотр дашборда', 'Доступ к дашборду, запускам синхронизации и журналам'),
       ('dashboard.sync.manage', 'Управление синхронизацией', 'Ручной запуск синхронизации из UI и API'),
       ('documents.view', 'Просмотр документов', 'Доступ к списку документов и карточкам документов'),
       ('commits.view', 'Просмотр коммитов', 'Доступ к разделу коммитов GitLab'),
       ('fileChanges.view', 'Просмотр истории файлов', 'Доступ к разделу истории файлов GitLab'),
       ('users.manage', 'Управление пользователями', 'Создание пользователей и назначение ролей и прав');

INSERT INTO app_role (code, name, description)
VALUES ('ADMIN', 'Администратор', 'Полный доступ к данным, синхронизации и управлению пользователями'),
       ('OPERATOR', 'Оператор', 'Работа с данными и ручной запуск синхронизации без управления доступом'),
       ('VIEWER', 'Наблюдатель', 'Только просмотр данных без ручного запуска синхронизации');

INSERT INTO app_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
JOIN app_permission p ON p.code IN (
    'dashboard.view',
    'dashboard.sync.manage',
    'documents.view',
    'commits.view',
    'fileChanges.view',
    'users.manage'
)
WHERE r.code = 'ADMIN';

INSERT INTO app_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
JOIN app_permission p ON p.code IN (
    'dashboard.view',
    'dashboard.sync.manage',
    'documents.view',
    'commits.view',
    'fileChanges.view'
)
WHERE r.code = 'OPERATOR';

INSERT INTO app_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
JOIN app_permission p ON p.code IN (
    'dashboard.view',
    'documents.view',
    'commits.view',
    'fileChanges.view'
)
WHERE r.code = 'VIEWER';
