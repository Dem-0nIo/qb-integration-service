// loadtest.js — prueba de carga del endpoint /api/client/consolidated-view
//
// PRERREQUISITOS:
//   1. Instalar k6: brew install k6
//   2. Generar tokens.json con TokenGenerator.java (ver ese archivo)
//   3. Colocar tokens.json en la misma carpeta que este script
//   4. Backend corriendo en local (por defecto http://localhost:8080)
//
// EJECUCIÓN:
//   k6 run loadtest.js
//
// Para cambiar el host o los meses consultados sin editar el script:
//   k6 run -e BASE_URL=http://localhost:8080 -e MONTHS=12 loadtest.js

import http from 'k6/http';
import { check, sleep } from 'k6';
import { SharedArray } from 'k6/data';

// Carga los tokens una sola vez y los comparte entre VUs (usuarios virtuales)
const tokens = new SharedArray('tokens', function () {
  return JSON.parse(open('./tokens.json'));
});

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const MONTHS = __ENV.MONTHS || '12';

export const options = {
  scenarios: {
    carga_portal: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '30s', target: 5 },   // calentamiento
        { duration: '1m', target: 10 },   // carga moderada — escenario piloto realista
        { duration: '1m', target: 20 },   // carga alta — margen sobre el piloto
        { duration: '30s', target: 0 },   // enfriamiento
      ],
    },
  },
  thresholds: {
    // Ajusta estos umbrales según lo que definas como aceptable para la tesis
    http_req_duration: ['p(95)<800', 'p(99)<1500'],
    http_req_failed: ['rate<0.01'], // menos de 1% de errores
  },
};

export default function () {
  const client = tokens[Math.floor(Math.random() * tokens.length)];

  const res = http.get(
    `${BASE_URL}/api/client/consolidated-view?months=${MONTHS}`,
    {
      headers: {
        Authorization: `Bearer ${client.token}`,
      },
      tags: { qbEmpresa: client.qbEmpresa },
    }
  );

  check(res, {
    'status es 200': (r) => r.status === 200,
    'respuesta no vacía': (r) => r.body && r.body.length > 0,
  });

  // Simula tiempo de lectura del cliente antes de la siguiente petición
  sleep(Math.random() * 2 + 1);
}
