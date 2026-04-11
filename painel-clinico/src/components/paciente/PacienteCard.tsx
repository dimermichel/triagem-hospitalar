import { Paciente, COR_CONFIG } from '../../types';
import { formatDistanceToNow } from 'date-fns';
import { ptBR } from 'date-fns/locale';
import styles from './PacienteCard.module.css';

interface Props {
  paciente: Paciente;
  posicao: number;
  onIniciarAtendimento: (triagemId: string) => void;
}

export function PacienteCard({ paciente, posicao, onIniciarAtendimento }: Props) {
  const cfg = COR_CONFIG[paciente.cor];
  const criadoEmDate = paciente.criado_em ? new Date(paciente.criado_em) : null;
  const chegouHa = criadoEmDate && !isNaN(criadoEmDate.getTime())
    ? formatDistanceToNow(criadoEmDate, { locale: ptBR, addSuffix: true })
    : '—';

  const isUrgente = paciente.cor === 'VERMELHO' || paciente.cor === 'LARANJA';

  return (
    <div
      className={`${styles.card} ${isUrgente ? styles.urgente : ''}`}
      style={{
        '--cor-border': cfg.border,
        '--cor-bg': cfg.bg,
        '--cor-text': cfg.text,
        '--cor-badge': cfg.badge,
      } as React.CSSProperties}
    >
      <div className={styles.leftBar} />

      <div className={styles.posicao}>#{posicao}</div>

      <div className={styles.content}>
        <div className={styles.header}>
          <span className={styles.nome}>{paciente.nome}</span>
          <span className={styles.idade}>{paciente.idade} anos</span>
          <span
            className={styles.corBadge}
          >
            {cfg.emoji} {cfg.label}
          </span>
        </div>

        <p className={styles.queixa}>{paciente.queixa_principal}</p>

        <div className={styles.meta}>
          <span className={styles.metaItem}>
            <span className={styles.metaLabel}>Chegou</span> {chegouHa}
          </span>
          {paciente.tempo_max_espera_min > 0 && (
            <span className={styles.metaItem}>
              <span className={styles.metaLabel}>Espera máx.</span>{' '}
              {paciente.tempo_max_espera_min} min
            </span>
          )}
          {paciente.tempo_max_espera_min === 0 && (
            <span className={styles.metaItem} style={{ color: '#c53030', fontWeight: 600 }}>
              Atendimento imediato
            </span>
          )}
        </div>
      </div>

      <button
        className={styles.btnAtender}
        onClick={() => onIniciarAtendimento(paciente.triagem_id)}
        aria-label={`Iniciar atendimento de ${paciente.nome}`}
      >
        Chamar
      </button>
    </div>
  );
}
