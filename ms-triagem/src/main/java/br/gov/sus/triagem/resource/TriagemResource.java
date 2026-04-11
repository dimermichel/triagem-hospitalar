package br.gov.sus.triagem.resource;

import br.gov.sus.triagem.dto.TriagemRequestDTO;
import br.gov.sus.triagem.dto.TriagemResponseDTO;
import br.gov.sus.triagem.service.TriagemService;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jboss.logging.Logger;

import java.util.UUID;

@Path("/api/v1/triagem")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Triagem", description = "Recepção e validação de dados clínicos para triagem")
public class TriagemResource {

    private static final Logger LOG = Logger.getLogger(TriagemResource.class);

    @Inject
    TriagemService triagemService;

    @POST
    @Operation(summary = "Registrar novo paciente para triagem",
               description = "Recebe dados clínicos, valida e envia para classificação Manchester via SQS")
    public Response registrar(@Valid TriagemRequestDTO request) {
        LOG.infof("Nova triagem recebida: paciente=%s, queixa=%s", request.getNome(), request.getQueixaPrincipal());

        String triagemId = UUID.randomUUID().toString();
        TriagemResponseDTO response = triagemService.processarTriagem(triagemId, request);

        LOG.infof("Triagem enviada para classificação: id=%s", triagemId);
        return Response.accepted(response).build();
    }

    @GET
    @Path("/{triagemId}/status")
    @Operation(summary = "Consultar status de uma triagem")
    public Response consultarStatus(@PathParam("triagemId") String triagemId) {
        return triagemService.buscarStatus(triagemId)
                .map(status -> Response.ok(status).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @POST
    @Path("/{triagemId}/iniciar-atendimento")
    @Operation(summary = "Iniciar atendimento — transiciona paciente para em atendimento")
    public Response iniciarAtendimento(@PathParam("triagemId") String triagemId,
                                       @QueryParam("profissionalId") String profissionalId) {
        LOG.infof("Iniciando atendimento: triagemId=%s, profissional=%s", triagemId, profissionalId);
        triagemService.iniciarAtendimento(triagemId, profissionalId);
        return Response.noContent().build();
    }

    @POST
    @Path("/{triagemId}/finalizar-atendimento")
    @Operation(summary = "Finalizar atendimento — registra término da consulta")
    public Response finalizarAtendimento(@PathParam("triagemId") String triagemId) {
        LOG.infof("Finalizando atendimento: triagemId=%s", triagemId);
        triagemService.finalizarAtendimento(triagemId);
        return Response.noContent().build();
    }
}
