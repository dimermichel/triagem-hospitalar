import { useMemo } from 'react';
import { Paciente, CorManchester, COR_CONFIG } from '../../types';
import { PacienteCard } from '../paciente/PacienteCard';
import styles from './FilaPanel.module.css';

interface Props {
  fila: Paciente[];
  onIniciarAtendimento: (triagemId: string) => void;
}

const ORDEM_CORES: CorManchester[] = ['VERMELHO', 'LARANJA', 'AMARELO', 'VERDE', 'AZUL'];

export function FilaPanel({ fila, onIniciarAtendimento }: Props) {
  const porCor = useMemo(() => {
    const mapa: Partial<Record<CorManchester, Paciente[]>> = {};
    for (const p of fila) {
      if (!mapa[p.cor]) mapa[p.cor] = [];
      mapa[p.cor]!.push(p);
    }
    return mapa;
  }, [fila]);

  if (fila.length === 0) {
    return (
      <div className={styles.vazia}>
        <span className={styles.vaziaIcon}>🏥</span>
        <p>Fila vazia — nenhum paciente aguardando</p>
      </div>
    );
  }

  let posicaoGlobal = 0;

  return (
    <div className={styles.painel}>
      <div className={styles.cabecalho}>
        <span className={styles.titulo}>Aguardando</span>
      </div>
      {ORDEM_CORES.map(cor => {
        const pacientes = porCor[cor];
        if (!pacientes || pacientes.length === 0) return null;
        const cfg = COR_CONFIG[cor];

        return (
          <section key={cor} className={styles.secao}>
            <div className={styles.secaoHeader} style={{ borderColor: cfg.border }}>
              <span className={styles.secaoEmoji}>{cfg.emoji}</span>
              <span className={styles.secaoLabel} style={{ color: cfg.text }}>
                {cfg.label}
              </span>
              <span
                className={styles.secaoContagem}
                style={{ background: cfg.badge, color: '#fff' }}
              >
                {pacientes.length}
              </span>
              {cfg.tempoMax === 0 && (
                <span className={styles.alerta}>Atendimento imediato</span>
              )}
              {cfg.tempoMax > 0 && (
                <span className={styles.tempoMax}>
                  Máx. {cfg.tempoMax} min
                </span>
              )}
            </div>

            <div className={styles.lista}>
              {pacientes.map(p => {
                posicaoGlobal++;
                return (
                  <PacienteCard
                    key={p.triagem_id}
                    paciente={p}
                    posicao={posicaoGlobal}
                    onIniciarAtendimento={onIniciarAtendimento}
                  />
                );
              })}
            </div>
          </section>
        );
      })}
    </div>
  );
}
