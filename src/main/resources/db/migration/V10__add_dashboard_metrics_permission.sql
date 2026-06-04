INSERT INTO app_permission (code, name, description)
VALUES (
    'dashboard.metrics.view',
    'Просмотр доп. метрик дашборда',
    'Доступ к блоку дополнительных метрик GitLab на дашборде'
)
ON CONFLICT (code) DO NOTHING;

INSERT INTO app_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
JOIN app_permission p ON p.code = 'dashboard.metrics.view'
WHERE r.code IN ('ADMIN', 'OPERATOR')
ON CONFLICT DO NOTHING;
