export type CorManchester = 'VERMELHO' | 'LARANJA' | 'AMARELO' | 'VERDE' | 'AZUL';

export interface SinaisVitais {
  pressao_sistolica: number;
  pressao_diastolica: number;
  frequencia_cardiaca: number;
  temperatura: number;
  saturacao_oxigenio: number;
  frequencia_respiratoria?: number;
}

export interface Paciente {
  triagem_id: string;
  hospital_id: string;
  nome: string;
  idade: number;
  queixa_principal: string;
  cor: CorManchester;
  emoji: string;
  descricao_cor: string;
  tempo_max_espera_min: number;
  prioridade_ordem: number;
  sinais_vitais?: SinaisVitais;
  status: 'AGUARDANDO_CLASSIFICACAO' | 'AGUARDANDO_ATENDIMENTO' | 'EM_ATENDIMENTO' | 'FINALIZADO';
  classificado_em: string;
  criado_em: string;
}

export interface EventoWS {
  tipo: 'NOVO_PACIENTE' | 'PACIENTE_REMOVIDO' | 'STATUS_ATUALIZADO' | 'SNAPSHOT_FILA' | 'PING';
  triagem_id?: string;
  paciente?: Paciente;
  pacientes?: Partial<Paciente>[];
  cor?: CorManchester;
}

export const COR_CONFIG: Record<CorManchester, {
  label: string;
  emoji: string;
  bg: string;
  text: string;
  border: string;
  badge: string;
  tempoMax: number;
}> = {
  VERMELHO: {
    label: 'Emergência',
    emoji: '🔴',
    bg: '#fff0f0',
    text: '#7a0000',
    border: '#ff4d4d',
    badge: '#e53e3e',
    tempoMax: 0,
  },
  LARANJA: {
    label: 'Muito urgente',
    emoji: '🟠',
    bg: '#fff5ec',
    text: '#7a3200',
    border: '#ff8c00',
    badge: '#dd6b20',
    tempoMax: 10,
  },
  AMARELO: {
    label: 'Urgente',
    emoji: '🟡',
    bg: '#fffbeb',
    text: '#6b4800',
    border: '#f6c90e',
    badge: '#d69e2e',
    tempoMax: 60,
  },
  VERDE: {
    label: 'Pouco urgente',
    emoji: '🟢',
    bg: '#f0fff4',
    text: '#1a4731',
    border: '#38a169',
    badge: '#38a169',
    tempoMax: 120,
  },
  AZUL: {
    label: 'Não urgente',
    emoji: '🔵',
    bg: '#ebf8ff',
    text: '#1a365d',
    border: '#4299e1',
    badge: '#3182ce',
    tempoMax: 240,
  },
};
