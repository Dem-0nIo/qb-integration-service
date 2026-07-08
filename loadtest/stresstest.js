// stresstest.js — encuentra el punto de degradación del endpoint
// /api/client/consolidated-view, subiendo por escalones hasta 150 VUs
// (más del triple de concurrencia realista para un universo de 220 clientes).
//
// Diferencia clave vs loadtest.js: aquí NO buscamos pasar/fallar un umbral
// fijo, sino OBSERVAR en qué escalón empieza a degradarse el sistema
// (tiempos de respuesta que dejan de ser lineales, errores de pool de
// conexiones, timeouts, etc.) para decidir si hace falta refactorizar algo.
//
// EJECUCIÓN:
//   k6 run stresstest.js
//
// Requiere el mismo tokens.json que loadtest.js (mismos 10 clientes reales).

import http from 'k6/http';
import { check } from 'k6';
import { SharedArray } from 'k6/data';
import { Trend, Counter } from 'k6/metrics';

const tokens = new SharedArray('tokens', function () {
    return JSON.parse(open('./tokens.json'));
});

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MONTHS = __ENV.MONTHS || '12';

// Métricas custom por escalón, para poder comparar el p95 de cada nivel
// de carga por separado en el resumen final.
const durationByStage = {};
['s20', 's50', 's80', 's110', 's150'].forEach(s => {
    durationByStage[s] = new Trend(`duration_${s}`, true);
});
const errorsByStage = {};
['s20', 's50', 's80', 's110', 's150'].forEach(s => {
    errorsByStage[s] = new Counter(`errors_${s}`);
});

export const options = {
    scenarios: {
        prueba_de_estres: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '30s', target: 20 },   // calentamiento — carga piloto real
                { duration: '45s', target: 20 },   // sostener: línea base
                { duration: '30s', target: 50 },   // subida — "más que suficiente" según David
                { duration: '45s', target: 50 },   // sostener
                { duration: '30s', target: 80 },   // subida — empieza zona de estrés
                { duration: '45s', target: 80 },   // sostener
                { duration: '30s', target: 110 },  // subida
                { duration: '45s', target: 110 },  // sostener
                { duration: '30s', target: 150 },  // techo — ~3x uso realista máximo
                { duration: '45s', target: 150 },  // sostener
                { duration: '30s', target: 0 },    // enfriamiento
            ],
            gracefulRampDown: '15s',
        },
    },
    // Umbrales solo informativos aquí — no abortamos el test si se cruzan,
    // porque el objetivo es observar hasta dónde llega, no pasar/fallar.
    thresholds: {
        http_req_duration: ['p(95)<5000'], // techo generoso, solo para no perder de vista casos extremos
    },
};

// Clasificamos cada request por el VU activo que lo hizo (ver más abajo),
// ya que k6 asigna VUs de forma creciente conforme sube el escenario —
// es un proxy simple y confiable del escalón de carga vigente.

export default function () {
    const client = tokens[Math.floor(Math.random() * tokens.length)];

    const res = http.get(
        `${BASE_URL}/api/client/consolidated-view?months=${MONTHS}`,
        {
            headers: { Authorization: `Bearer ${client.token}` },
            tags: { qbEmpresa: client.qbEmpresa },
            timeout: '10s', // evita que un request colgado bloquee el VU indefinidamente
        }
    );

    const ok = check(res, {
        'status es 200': (r) => r.status === 200,
        'respuesta no vacía': (r) => r.body && r.body.length > 0,
        'no fue timeout': (r) => r.status !== 0,
    });

    // Clasifica por el VU actual (proxy razonable del escalón de carga activo,
    // ya que k6 asigna VUs de forma creciente conforme sube el escenario)
    const vu = __VU;
    let stage;
    if (vu <= 20) stage = 's20';
    else if (vu <= 50) stage = 's50';
    else if (vu <= 80) stage = 's80';
    else if (vu <= 110) stage = 's110';
    else stage = 's150';

    durationByStage[stage].add(res.timings.duration);
    if (!ok) errorsByStage[stage].add(1);
}