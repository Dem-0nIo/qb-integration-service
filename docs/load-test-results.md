# Historial de pruebas de carga (k6)

## 2026-07-02 — Pilot test post-fix HikariCP
- **p95:** 458ms
- **Error rate:** 0%
- **Contexto:** se identificó cuello de botella en el pool de conexiones HikariCP;
  se ajustó a 8/6 tras verificar capacidad de MariaDB.
- **Fix relacionado:** corregido bloque duplicado en `application.properties`
  (restaurado `wordpress.datasource.username` a variable de entorno).
- **Script:** `src/test/java/com/aaelevator/qbintegration/loadtest/stresstest.js`

## Pendiente — Stress test 150 VUs (producción)
- **Estado:** no ejecutado aún, bloqueado hasta publicación del sitio en Lightsail.
- **Objetivo:** validar bajo carga real vía proxy de producción, incluyendo
  spot-check de headers Cache-Control y headers de seguridad.
