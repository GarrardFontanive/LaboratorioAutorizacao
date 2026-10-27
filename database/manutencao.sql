SELECT id, nome, codigo, conflitos FROM empresas ORDER BY id;

SELECT u.id, u.login, e.nome AS empresa, v.perfil
FROM usuarios u
JOIN vinculos v ON v.usuario_id = u.id
JOIN empresas e ON e.id = v.empresa_id
ORDER BY u.login, e.nome;

SELECT u.login, e.nome AS empresa, a.primeiro_acesso
FROM acessos_administrativos a
JOIN usuarios u ON u.id = a.usuario_id
JOIN empresas e ON e.id = a.empresa_id
ORDER BY u.login, a.primeiro_acesso;

INSERT INTO empresas (id, nome, codigo, conflitos)
VALUES (4, 'Oceano Consultoria', 'oceano', 'verde');

UPDATE empresas SET conflitos = 'oceano' WHERE codigo = 'verde';

INSERT INTO vinculos (usuario_id, empresa_id, perfil)
SELECT u.id, e.id, 'ADMIN'
FROM usuarios u CROSS JOIN empresas e
WHERE u.login = 'admin' AND e.codigo = 'oceano'
AND NOT EXISTS (
    SELECT 1 FROM vinculos v WHERE v.usuario_id = u.id AND v.empresa_id = e.id
);
