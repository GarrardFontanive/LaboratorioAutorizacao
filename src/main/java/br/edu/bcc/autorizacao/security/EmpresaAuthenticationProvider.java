package br.edu.bcc.autorizacao.security;

import br.edu.bcc.autorizacao.service.LaboratorioService;
import java.util.List;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

@Component
public class EmpresaAuthenticationProvider implements AuthenticationProvider {
    private final LaboratorioService laboratorio;

    public EmpresaAuthenticationProvider(LaboratorioService laboratorio) {
        this.laboratorio = laboratorio;
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        if (!(authentication.getDetails() instanceof EmpresaLoginDetails details)) {
            throw new BadCredentialsException("Informe curso, usuário e senha.");
        }
        UsuarioAutenticado usuario = laboratorio.autenticar(details.empresa(),
                authentication.getName(), String.valueOf(authentication.getCredentials()));
        return UsernamePasswordAuthenticationToken.authenticated(usuario, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + usuario.perfil())));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
