package br.edu.bcc.autorizacao.web;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

@ControllerAdvice
public class TratamentoErros {
    @ExceptionHandler(AccessDeniedException.class)
    String proibido(AccessDeniedException exception, HttpServletResponse response, Model model) {
        return erro(403, exception.getMessage(), response, model);
    }

    @ExceptionHandler(ResponseStatusException.class)
    String registroInvalido(ResponseStatusException exception, HttpServletResponse response, Model model) {
        return erro(exception.getStatusCode().value(), exception.getReason(), response, model);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    String formularioInvalido(HttpServletResponse response, Model model) {
        return erro(HttpStatus.BAD_REQUEST.value(), "Confira os números e as datas informados no formulário.", response, model);
    }

    private String erro(int status, String mensagem, HttpServletResponse response, Model model) {
        response.setStatus(status);
        model.addAttribute("status", status);
        model.addAttribute("mensagem", mensagem);
        return "error";
    }
}
