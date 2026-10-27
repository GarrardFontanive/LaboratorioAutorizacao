package br.edu.bcc.autorizacao.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

public class EmpresaLoginDetails extends WebAuthenticationDetails {
    private final String empresa;

    public EmpresaLoginDetails(HttpServletRequest request) {
        super(request);
        empresa = request.getParameter("empresa");
    }

    public String empresa() {
        return empresa;
    }
}
