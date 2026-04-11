import { useCallback, useEffect, useRef, useState } from 'react';
import { COR_CONFIG, CorManchester, EventoWS, Paciente } from '../types';

const WS_URL = import.meta.env.VITE_WS_URL ?? 'ws://localhost:3001';
const RECONNECT_DELAY_MS = 3000;

export type ConnectionStatus = 'conectando' | 'conectado' | 'desconectado' | 'erro';

export function useFilaWebSocket() {
  const [fila, setFila] = useState<Paciente[]>([]);
  const [emAtendimento, setEmAtendimento] = useState<Paciente[]>([]);
  const [status, setStatus] = useState<ConnectionStatus>('conectando');
  const wsRef = useRef<WebSocket | null>(null);
  const reconnectTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

  const conectar = useCallback(() => {
    if (wsRef.current?.readyState === WebSocket.OPEN) return;

    setStatus('conectando');
    const ws = new WebSocket(WS_URL);
    wsRef.current = ws;

    ws.onopen = () => {
      setStatus('conectado');
      // Solicita snapshot da fila atual ao conectar
      ws.send(JSON.stringify({ tipo: 'GET_FILA' }));
    };

    ws.onmessage = (evt) => {
      try {
        const evento: EventoWS = JSON.parse(evt.data);
        handleEvento(evento);
      } catch {
        console.error('Mensagem WS inválida:', evt.data);
      }
    };

    ws.onerror = () => setStatus('erro');

    ws.onclose = () => {
      setStatus('desconectado');
      reconnectTimer.current = setTimeout(conectar, RECONNECT_DELAY_MS);
    };
  }, []);

  const handleEvento = (evento: EventoWS) => {
    switch (evento.tipo) {
      case 'SNAPSHOT_FILA':
        if (evento.pacientes) {
          const todos = evento.pacientes
            .filter(p => !!p.triagem_id && (p.status === 'EM_ATENDIMENTO' || (p.cor && p.cor in COR_CONFIG)))
            .map(p => ({
              ...p,
              cor:           (p.cor && p.cor in COR_CONFIG ? p.cor : 'AZUL') as CorManchester,
              emoji:         p.emoji         ?? COR_CONFIG[(p.cor as CorManchester) ?? 'AZUL']?.emoji ?? '⬜',
              descricao_cor: p.descricao_cor  ?? COR_CONFIG[(p.cor as CorManchester) ?? 'AZUL']?.label ?? 'Não classificado',
              status:        p.status         ?? 'AGUARDANDO_ATENDIMENTO',
              hospital_id:   p.hospital_id    ?? '',
            })) as Paciente[];
          setFila(ordenarFila(todos.filter(p => p.status !== 'EM_ATENDIMENTO')));
          setEmAtendimento(prev => {
            const incoming = todos.filter(p => p.status === 'EM_ATENDIMENTO');
            const merged = [...prev];
            for (const p of incoming) {
              if (!merged.some(e => e.triagem_id === p.triagem_id)) merged.push(p);
            }
            return merged;
          });
        }
        break;

      case 'NOVO_PACIENTE':
        if (evento.paciente) {
          setFila(prev => {
            // Evita duplicatas
            const semDuplicata = prev.filter(p => p.triagem_id !== evento.paciente!.triagem_id);
            const nova = [...semDuplicata, evento.paciente!];
            return ordenarFila(nova);
          });
        }
        break;

      case 'PACIENTE_REMOVIDO':
        if (evento.triagem_id) {
          setFila(prev => prev.filter(p => p.triagem_id !== evento.triagem_id));
        }
        break;

      case 'STATUS_ATUALIZADO':
        if (evento.triagem_id && evento.paciente) {
          setFila(prev =>
            prev.map(p => p.triagem_id === evento.triagem_id ? evento.paciente! : p)
          );
        }
        break;
    }
  };

  const iniciarAtendimento = useCallback(async (triagemId: string) => {
    const apiUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
    await fetch(`${apiUrl}/api/v1/triagem/${triagemId}/iniciar-atendimento`, {
      method: 'POST',
    });
    setFila(prev => {
      const paciente = prev.find(p => p.triagem_id === triagemId);
      if (paciente) {
        setEmAtendimento(ea =>
          ea.some(p => p.triagem_id === triagemId)
            ? ea
            : [...ea, { ...paciente, status: 'EM_ATENDIMENTO' }]
        );
      }
      return prev.filter(p => p.triagem_id !== triagemId);
    });
  }, []);

  const finalizarAtendimento = useCallback(async (triagemId: string) => {
    const apiUrl = import.meta.env.VITE_API_URL ?? 'http://localhost:8080';
    await fetch(`${apiUrl}/api/v1/triagem/${triagemId}/finalizar-atendimento`, {
      method: 'POST',
    });
    setEmAtendimento(prev => prev.filter(p => p.triagem_id !== triagemId));
  }, []);

  useEffect(() => {
    conectar();
    return () => {
      if (reconnectTimer.current) clearTimeout(reconnectTimer.current);
      wsRef.current?.close();
    };
  }, [conectar]);

  return { fila, emAtendimento, status, iniciarAtendimento, finalizarAtendimento };
}

function ordenarFila(pacientes: Paciente[]): Paciente[] {
  return [...pacientes].sort((a, b) => {
    // Primeiro por prioridade (1 = mais urgente)
    if (a.prioridade_ordem !== b.prioridade_ordem) {
      return a.prioridade_ordem - b.prioridade_ordem;
    }
    // Depois por hora de chegada (mais antigo primeiro)
    return new Date(a.criado_em).getTime() - new Date(b.criado_em).getTime();
  });
}
