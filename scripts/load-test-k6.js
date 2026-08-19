import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    create_payments: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 10),
      duration: __ENV.DURATION || '1m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.05'],
    http_req_duration: ['p(95)<1000'],
  },
};

export default function () {
  const idempotencyKey = `load-${__VU}-${__ITER}-${Date.now()}`;
  const response = http.post(
    'http://localhost:8081/api/v1/payments',
    JSON.stringify({
      merchantId: 'merchant-load',
      amount: 100.0,
      currency: 'BRL',
      paymentMethod: 'PIX',
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'Idempotency-Key': idempotencyKey,
        'X-Correlation-Id': idempotencyKey,
      },
    },
  );

  check(response, {
    'payment create is 2xx': (r) => r.status >= 200 && r.status < 300,
  });

  sleep(1);
}
