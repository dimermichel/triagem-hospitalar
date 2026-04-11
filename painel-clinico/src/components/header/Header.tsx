import { Paciente, COR_CONFIG, CorManchester } from '../../types';
import { ConnectionStatus } from '../../hooks/useFilaWebSocket';
import { FiltroStatus } from '../../App';
import styles from './Header.module.css';

interface Props {
  fila: Paciente[];
  emAtendimento: Paciente[];
  status: ConnectionStatus;
  filtro: FiltroStatus;
  onFiltroChange: (f: FiltroStatus) => void;
}

const HOSPITAL_NOME = import.meta.env.VITE_HOSPITAL_NOME ?? 'Unidade de Saúde';

const STATUS_CONFIG: Record<ConnectionStatus, { label: string; cor: string }> = {
  conectando:    { label: 'Conectando…',  cor: '#d69e2e' },
  conectado:     { label: 'Ao vivo',      cor: '#38a169' },
  desconectado:  { label: 'Reconectando', cor: '#e53e3e' },
  erro:          { label: 'Erro WS',      cor: '#e53e3e' },
};

export function Header({ fila, emAtendimento, status, filtro, onFiltroChange }: Props) {
  const cfg = STATUS_CONFIG[status];

  const listaAtiva = filtro === 'AGUARDANDO_ATENDIMENTO' ? fila : emAtendimento;

  const contagemPorCor = (cor: CorManchester) =>
    listaAtiva.filter(p => p.cor === cor).length;

  return (
    <header className={styles.header}>
      <div className={styles.left}>
        <span className={styles.icone}>🏥</span>
        <div>
          <h1 className={styles.titulo}>Painel de Triagem</h1>
          <p className={styles.hospital}>{HOSPITAL_NOME}</p>
        </div>
      </div>

      <div className={styles.stats}>
        {(['VERMELHO', 'LARANJA', 'AMARELO', 'VERDE', 'AZUL'] as CorManchester[]).map(cor => {
          const n = contagemPorCor(cor);
          if (n === 0) return null;
          const c = COR_CONFIG[cor];
          return (
            <div
              key={cor}
              className={styles.statItem}
              style={{ background: c.bg, borderColor: c.border }}
              title={c.label}
            >
              <span>{c.emoji}</span>
              <span className={styles.statNum} style={{ color: c.text }}>{n}</span>
            </div>
          );
        })}
        <div className={styles.statItem} style={{ background: '#f7fafc', borderColor: '#e2e8f0' }}>
          <span className={styles.statLabel}>Total</span>
          <span className={styles.statNum}>{listaAtiva.length}</span>
        </div>
      </div>

      <div className={styles.right}>
        <div className={styles.toggle}>
          <button
            className={`${styles.toggleBtn} ${filtro === 'AGUARDANDO_ATENDIMENTO' ? styles.toggleActive : ''}`}
            onClick={() => onFiltroChange('AGUARDANDO_ATENDIMENTO')}
          >
            Aguardando
          </button>
          <button
            className={`${styles.toggleBtn} ${filtro === 'EM_ATENDIMENTO' ? styles.toggleActive : ''}`}
            onClick={() => onFiltroChange('EM_ATENDIMENTO')}
          >
            Em atendimento
          </button>
        </div>
        <span className={styles.statusDot} style={{ background: cfg.cor }} />
        <span className={styles.statusLabel} style={{ color: cfg.cor }}>{cfg.label}</span>
        <span className={styles.hora} suppressHydrationWarning>
          {new Date().toLocaleTimeString('pt-BR', { hour: '2-digit', minute: '2-digit' })}
        </span>
      </div>
    </header>
  );
}
