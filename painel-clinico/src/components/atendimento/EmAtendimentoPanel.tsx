import { Paciente, COR_CONFIG } from '../../types';
import { formatDistanceToNow } from 'date-fns';
import { ptBR } from 'date-fns/locale';
import styles from './EmAtendimentoPanel.module.css';

interface Props {
  emAtendimento: Paciente[];
  onFinalizar: (triagemId: string) => void;
}

export function EmAtendimentoPanel({ emAtendimento, onFinalizar }: Props) {

    if (emAtendimento.length === 0) {
        return (
            <div className={styles.vazia}>
                <span className={styles.vaziaIcon}>🏥</span>
                <p>Nenhum paciente em atendimento</p>
            </div>
        );
    }

  return (
    <section className={styles.painel}>
      <div className={styles.cabecalho}>
        <span className={styles.titulo}>Em atendimento</span>
      </div>

      <div className={styles.lista}>
        {emAtendimento.map(paciente => {
          const cfg = COR_CONFIG[paciente.cor];
          const criadoEmDate = paciente.criado_em ? new Date(paciente.criado_em) : null;
          const chegouHa = criadoEmDate && !isNaN(criadoEmDate.getTime())
            ? formatDistanceToNow(criadoEmDate, { locale: ptBR, addSuffix: true })
            : '—';

          return (
            <div
              key={paciente.triagem_id}
              className={styles.card}
              style={{
                '--cor-border': cfg.border,
                '--cor-bg': cfg.bg,
                '--cor-text': cfg.text,
                '--cor-badge': cfg.badge,
              } as React.CSSProperties}
            >
              <div className={styles.leftBar} />

              <div className={styles.content}>
                <div className={styles.header}>
                  <span className={styles.nome}>{paciente.nome}</span>
                  <span className={styles.idade}>{paciente.idade} anos</span>
                  <span className={styles.corBadge}>
                    {cfg.emoji} {cfg.label}
                  </span>
                </div>
                <p className={styles.queixa}>{paciente.queixa_principal}</p>
                <div className={styles.meta}>
                  <span className={styles.metaItem}>
                    <span className={styles.metaLabel}>Chegou</span> {chegouHa}
                  </span>
                </div>
              </div>

              <button
                className={styles.btnFinalizar}
                onClick={() => onFinalizar(paciente.triagem_id)}
                aria-label={`Finalizar atendimento de ${paciente.nome}`}
              >
                Finalizar
              </button>
            </div>
          );
        })}
      </div>
    </section>
  );
}
