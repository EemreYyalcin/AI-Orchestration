import { useEffect, useState } from 'react';

type Csrf = { token: string; headerName: string };
type Investigation = {
  id: string; status: string; approval: string;
  report: null | {
    degraded: boolean; diagnosticSourcesSimulated: boolean;
    classification: { incidentType: string; severity: string };
    evidence: { items: { id: string; source: string; summary: string }[]; failures: { source: string; failure: string }[] };
    findings: { summary: string; likelyCauses: string[]; recommendedActions: string[]; evidenceIds: string[]; proposedAction: string };
  };
};

const statuses = ['CREATED', 'ANALYZING', 'COLLECTING_EVIDENCE', 'REASONING', 'WAITING_APPROVAL', 'COMPLETED', 'FAILED'];
async function fetchInvestigation(path: string, init: RequestInit = {}): Promise<{ value: Investigation; pending: boolean }> {
  const response = await fetch(path, { credentials: 'same-origin', cache: 'no-store', signal: AbortSignal.timeout(10000), ...init });
  if (!response.ok && response.status !== 503) throw new Error(response.status === 401 ? 'Your session expired. Sign in again.'
    : response.status === 429 ? 'Start limit reached. Wait a minute before retrying.' : 'Request failed. Refresh state before retrying.');
  const value: unknown = await response.json();
  if (typeof value !== 'object' || value === null || !('id' in value) || typeof value.id !== 'string'
    || !('status' in value) || typeof value.status !== 'string' || !statuses.includes(value.status))
    throw new Error('Unable to confirm investigation state.');
  return { value: value as Investigation, pending: response.status === 503 };
}

export default function Investigations({ getCsrf }: { getCsrf: () => Promise<Csrf> }) {
  const [question, setQuestion] = useState('Why is the recommendation service failing?');
  const [value, setValue] = useState<Investigation | null>(null);
  const [busy, setBusy] = useState(false);
  const [message, setMessage] = useState('');
  const [polling, setPolling] = useState(true);
  const id = value?.id;
  const terminal = value?.status === 'COMPLETED' || value?.status === 'FAILED';
  useEffect(() => {
    if (!id || terminal || !polling) return;
    let active = true;
    let timer: ReturnType<typeof setTimeout>;
    async function poll() {
      try {
        const result = await fetchInvestigation(`/api/investigations/${encodeURIComponent(id!)}`);
        if (active) { setValue(result.value); timer = setTimeout(() => void poll(), 2000); }
      } catch (error) { if (active) { setMessage(error instanceof Error ? error.message : 'Polling failed.'); setPolling(false); } }
    }
    timer = setTimeout(() => void poll(), 1000);
    return () => { active = false; clearTimeout(timer); };
  }, [id, terminal, polling]);

  async function mutate(action?: 'approve' | 'deny' | 'run') {
    setBusy(true); setMessage('');
    try {
      const csrf = await getCsrf();
      const result = await fetchInvestigation(action ? `/api/investigations/${encodeURIComponent(id!)}/${action}` : '/api/investigations', {
        method: 'POST', headers: { 'Content-Type': 'application/json', [csrf.headerName]: csrf.token },
        ...(!action ? { body: JSON.stringify({ question: question.trim() }) } : {}),
      });
      setValue(result.value); setPolling(true);
      if (result.pending) setMessage('Workflow start is pending. The server will retry boundedly; use Retry start if it remains CREATED.');
    } catch (error) { setMessage(error instanceof Error ? error.message : 'Request failed.'); }
    finally { setBusy(false); }
  }

  return <section>
    <h2>Investigate an incident</h2>
    <p>Diagnostic sources are currently simulations. Approved remediation records a mock action only.</p>
    <form onSubmit={event => { event.preventDefault(); void mutate(); }}>
      <label htmlFor="question">Incident question</label><br />
      <textarea id="question" maxLength={4000} required rows={4} value={question} onChange={event => setQuestion(event.target.value)} />
      <br /><button disabled={busy || !question.trim() || (!!value && !terminal)}>Start investigation</button>
    </form>
    {message && <p role="alert">{message}</p>}
    {value && <div>
      <p role="status">Investigation {value.id}: <strong>{value.status}</strong></p>
      {!polling && <button onClick={() => { setMessage(''); setPolling(true); }}>Refresh status</button>}
      {value.status === 'CREATED' && <button disabled={busy} onClick={() => void mutate('run')}>Retry start</button>}
      {value.status === 'WAITING_APPROVAL' && <div>
        <p>Proposed mock action: {value.report?.findings.proposedAction}. Only approve after reviewing the evidence.</p>
        <button disabled={busy || value.approval !== 'PENDING'} onClick={() => void mutate('approve')}>Approve mock restart</button>
        <button disabled={busy || value.approval !== 'PENDING'} onClick={() => void mutate('deny')}>Deny</button>
      </div>}
      {value.report && <article>
        <h3>Incident report</h3>
        <p>{value.report.classification.incidentType} / {value.report.classification.severity}; approval: {value.approval}</p>
        {value.report.degraded && <p>Degraded evidence or model result: manual review required.</p>}
        <p>{value.report.findings.summary}</p>
        <h4>Evidence</h4><ul>{value.report.evidence.items.map(item => <li key={item.id}>[{item.id}] {item.source}: {item.summary}</li>)}</ul>
        <ul>{value.report.evidence.failures.map(item => <li key={item.source}>{item.source} unavailable: {item.failure}</li>)}</ul>
        <h4>Likely causes</h4><ul>{value.report.findings.likelyCauses.map(item => <li key={item}>{item}</li>)}</ul>
        <h4>Recommended actions</h4><ul>{value.report.findings.recommendedActions.map(item => <li key={item}>{item}</li>)}</ul>
        <p>Cited evidence: {value.report.findings.evidenceIds.join(', ')}</p>
      </article>}
    </div>}
  </section>;
}
