import { useEffect, useState } from 'react';
import { Header } from './components/header/Header';
import { FilaPanel } from './components/fila/FilaPanel';
import { EmAtendimentoPanel } from './components/atendimento/EmAtendimentoPanel';
import { useFilaWebSocket } from './hooks/useFilaWebSocket';
import './styles/global.css';

export type FiltroStatus = 'AGUARDANDO_ATENDIMENTO' | 'EM_ATENDIMENTO';

export default function App() {
  const { fila, emAtendimento, status, iniciarAtendimento, finalizarAtendimento } = useFilaWebSocket();
  const [filtro, setFiltro] = useState<FiltroStatus>('AGUARDANDO_ATENDIMENTO');
  const [, setHora] = useState(new Date());

  // Atualiza o relógio do header a cada minuto
  useEffect(() => {
    const timer = setInterval(() => setHora(new Date()), 60_000);
    return () => clearInterval(timer);
  }, []);

  return (
    <div className="app">
      <Header fila={fila} emAtendimento={emAtendimento} status={status} filtro={filtro} onFiltroChange={setFiltro} />
      <main className="main">
        {filtro === 'EM_ATENDIMENTO'
          ? <EmAtendimentoPanel emAtendimento={emAtendimento} onFinalizar={finalizarAtendimento} />
          : <FilaPanel fila={fila} onIniciarAtendimento={iniciarAtendimento} />
        }
      </main>
    </div>
  );
}
