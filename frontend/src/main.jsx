import React, { useEffect, useMemo, useState } from 'react'
import { createRoot } from 'react-dom/client'
import {
  Activity,
  AlertTriangle,
  CheckCircle2,
  Clipboard,
  CreditCard,
  FileJson,
  KeyRound,
  Loader2,
  RefreshCw,
  Search,
  ShieldCheck,
} from 'lucide-react'
import './styles.css'

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || ''
const FINAL_STATUSES = new Set(['APPROVED', 'REJECTED', 'FAILED'])

function App() {
  const [apiBaseUrl, setApiBaseUrl] = useLocalStorage('highpay.apiBaseUrl', API_BASE_URL)
  const [jwt, setJwt] = useLocalStorage('highpay.jwt', '')
  const [payments, setPayments] = useState([])
  const [selectedPayment, setSelectedPayment] = useState(null)
  const [health, setHealth] = useState('UNKNOWN')
  const [loading, setLoading] = useState(false)
  const [message, setMessage] = useState(null)
  const [form, setForm] = useState({
    merchantId: 'merchant-001',
    amount: '100.00',
    currency: 'BRL',
    paymentMethod: 'PIX',
  })

  const stats = useMemo(() => {
    const totals = { CREATED: 0, PROCESSING: 0, APPROVED: 0, REJECTED: 0, FAILED: 0 }
    for (const payment of payments) {
      totals[payment.status] = (totals[payment.status] || 0) + 1
    }
    return totals
  }, [payments])

  useEffect(() => {
    refreshAll()
  }, [])

  useEffect(() => {
    if (!selectedPayment || FINAL_STATUSES.has(selectedPayment.status)) {
      return undefined
    }

    const interval = setInterval(() => {
      fetchPayment(selectedPayment.id, { quiet: true })
    }, 2500)

    return () => clearInterval(interval)
  }, [selectedPayment?.id, selectedPayment?.status])

  async function refreshAll() {
    await Promise.allSettled([fetchHealth(), fetchPayments()])
  }

  async function request(path, options = {}) {
    const headers = {
      ...(options.headers || {}),
    }

    if (jwt.trim()) {
      headers.Authorization = `Bearer ${jwt.trim()}`
    }

    const response = await fetch(`${apiBaseUrl}${path}`, {
      ...options,
      headers,
    })

    if (!response.ok) {
      let errorMessage = `HTTP ${response.status}`
      try {
        const body = await response.json()
        errorMessage = body.message || errorMessage
      } catch {
        // Keep status message when the response is not JSON.
      }
      throw new Error(errorMessage)
    }

    return response.json()
  }

  async function fetchHealth() {
    try {
      const body = await request('/actuator/health')
      setHealth(body.status || 'UNKNOWN')
    } catch {
      setHealth('DOWN')
    }
  }

  async function fetchPayments() {
    try {
      const body = await request('/api/v1/payments?page=0&size=20')
      setPayments(body.items || [])
    } catch (error) {
      setMessage({ type: 'error', text: error.message })
    }
  }

  async function fetchPayment(id, options = {}) {
    try {
      const payment = await request(`/api/v1/payments/${id}`)
      setSelectedPayment(payment)
      setPayments((current) => current.map((item) => (item.id === payment.id ? payment : item)))
      return payment
    } catch (error) {
      if (!options.quiet) {
        setMessage({ type: 'error', text: error.message })
      }
      return null
    }
  }

  async function createPayment(event) {
    event.preventDefault()
    setLoading(true)
    setMessage(null)

    try {
      const idempotencyKey = `ui-${crypto.randomUUID()}`
      const payment = await request('/api/v1/payments', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Idempotency-Key': idempotencyKey,
          'X-Correlation-Id': `ui-${crypto.randomUUID()}`,
        },
        body: JSON.stringify({
          merchantId: form.merchantId,
          amount: Number(form.amount),
          currency: form.currency,
          paymentMethod: form.paymentMethod,
        }),
      })

      setSelectedPayment(payment)
      setMessage({ type: 'success', text: `Pagamento criado com status ${payment.status}` })
      await fetchPayments()
    } catch (error) {
      setMessage({ type: 'error', text: error.message })
    } finally {
      setLoading(false)
    }
  }

  function updateForm(field, value) {
    setForm((current) => ({ ...current, [field]: value }))
  }

  return (
    <main className="app-shell">
      <header className="topbar">
        <div>
          <h1>HighPay Console</h1>
          <p>Operacao de pagamentos, API REST, JWT, status assincrono e observabilidade.</p>
        </div>
        <div className={`health health-${health.toLowerCase()}`}>
          <Activity size={18} />
          <span>{health}</span>
        </div>
      </header>

      <section className="toolbar">
        <label>
          <span>API</span>
          <input
            value={apiBaseUrl}
            onChange={(event) => setApiBaseUrl(event.target.value)}
            placeholder="http://localhost:8081"
          />
        </label>
        <label>
          <span>JWT</span>
          <input
            value={jwt}
            onChange={(event) => setJwt(event.target.value)}
            placeholder="Bearer token opcional"
            type="password"
          />
        </label>
        <button className="icon-button" onClick={refreshAll} title="Atualizar">
          <RefreshCw size={18} />
        </button>
        <a className="link-button" href={`${apiBaseUrl}/swagger-ui.html`} target="_blank" rel="noreferrer">
          <FileJson size={18} />
          Swagger
        </a>
      </section>

      {message && (
        <div className={`notice notice-${message.type}`}>
          {message.type === 'success' ? <CheckCircle2 size={18} /> : <AlertTriangle size={18} />}
          <span>{message.text}</span>
        </div>
      )}

      <section className="stats-grid">
        <Stat label="Criados" value={stats.CREATED} />
        <Stat label="Processando" value={stats.PROCESSING} />
        <Stat label="Aprovados" value={stats.APPROVED} />
        <Stat label="Rejeitados" value={stats.REJECTED + stats.FAILED} />
      </section>

      <section className="workspace">
        <form className="panel payment-form" onSubmit={createPayment}>
          <div className="panel-title">
            <CreditCard size={20} />
            <h2>Novo Pagamento</h2>
          </div>

          <label>
            <span>Merchant</span>
            <input value={form.merchantId} onChange={(event) => updateForm('merchantId', event.target.value)} />
          </label>

          <label>
            <span>Valor</span>
            <input
              value={form.amount}
              onChange={(event) => updateForm('amount', event.target.value)}
              inputMode="decimal"
            />
          </label>

          <div className="field-row">
            <label>
              <span>Moeda</span>
              <input value={form.currency} onChange={(event) => updateForm('currency', event.target.value)} />
            </label>
            <label>
              <span>Metodo</span>
              <select value={form.paymentMethod} onChange={(event) => updateForm('paymentMethod', event.target.value)}>
                <option value="PIX">PIX</option>
              </select>
            </label>
          </div>

          <button className="primary-button" disabled={loading}>
            {loading ? <Loader2 className="spin" size={18} /> : <ShieldCheck size={18} />}
            Criar com Idempotency-Key
          </button>
        </form>

        <section className="panel payment-list">
          <div className="panel-title">
            <Search size={20} />
            <h2>Pagamentos Recentes</h2>
          </div>
          <div className="table">
            {payments.map((payment) => (
              <button
                key={payment.id}
                className={`row ${selectedPayment?.id === payment.id ? 'selected' : ''}`}
                onClick={() => fetchPayment(payment.id)}
              >
                <span className={`status-dot status-${payment.status.toLowerCase()}`} />
                <span className="row-id">{payment.id}</span>
                <span className="row-status">{payment.status}</span>
                <span className="row-amount">
                  {payment.currency} {payment.amount}
                </span>
              </button>
            ))}
          </div>
        </section>

        <section className="panel detail-panel">
          <div className="panel-title">
            <Clipboard size={20} />
            <h2>Detalhe</h2>
          </div>
          {selectedPayment ? (
            <dl>
              <Detail label="ID" value={selectedPayment.id} />
              <Detail label="Merchant" value={selectedPayment.merchantId} />
              <Detail label="Status" value={selectedPayment.status} />
              <Detail label="Valor" value={`${selectedPayment.currency} ${selectedPayment.amount}`} />
              <Detail label="Provider" value={selectedPayment.providerTransactionId || '-'} />
            </dl>
          ) : (
            <div className="empty-state">
              <KeyRound size={24} />
              <span>Selecione ou crie um pagamento.</span>
            </div>
          )}
        </section>
      </section>
    </main>
  )
}

function Stat({ label, value }) {
  return (
    <div className="stat">
      <span>{label}</span>
      <strong>{value}</strong>
    </div>
  )
}

function Detail({ label, value }) {
  return (
    <div className="detail-row">
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  )
}

function useLocalStorage(key, initialValue) {
  const [value, setValue] = useState(() => localStorage.getItem(key) ?? initialValue)

  useEffect(() => {
    localStorage.setItem(key, value)
  }, [key, value])

  return [value, setValue]
}

createRoot(document.getElementById('root')).render(<App />)
