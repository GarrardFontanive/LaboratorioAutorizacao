package br.edu.bcc.autorizacao.web;

import br.edu.bcc.autorizacao.security.UsuarioAutenticado;
import br.edu.bcc.autorizacao.service.LaboratorioService;
import br.edu.bcc.autorizacao.service.Modelos.Conta;
import br.edu.bcc.autorizacao.service.Modelos.Noticia;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.WebAttributes;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class LaboratorioController {
    private final LaboratorioService laboratorio;

    public LaboratorioController(LaboratorioService laboratorio) {
        this.laboratorio = laboratorio;
    }

    @ModelAttribute
    void usuario(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        model.addAttribute("usuario", usuario);
    }

    @GetMapping("/")
    String publicacoes(Model model) {
        model.addAttribute("noticias", laboratorio.noticiasPublicas());
        return "publico";
    }

    @GetMapping("/login")
    String login(HttpServletRequest request, Model model) {
        model.addAttribute("empresas", laboratorio.empresas());
        if (request.getParameter("erro") != null && request.getSession(false) != null) {
            Object exception = request.getSession(false).getAttribute(WebAttributes.AUTHENTICATION_EXCEPTION);
            model.addAttribute("erroLogin", exception instanceof org.springframework.security.core.AuthenticationException falha
                    ? falha.getMessage() : "Não foi possível entrar. Confira suas credenciais.");
        }
        return "login";
    }

    @GetMapping("/painel")
    String painel(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        model.addAttribute("totalNoticias", laboratorio.noticias(usuario).size());
        model.addAttribute("totalContas", laboratorio.contas(usuario).size());
        model.addAttribute("vinculos", laboratorio.vinculos(usuario));
        model.addAttribute("historico", laboratorio.historicoAdministrativo(usuario));
        return "painel";
    }

    @PostMapping("/trocar-empresa")
    String trocarEmpresa(@AuthenticationPrincipal UsuarioAutenticado usuario, @RequestParam long empresaId,
            HttpServletRequest request, HttpServletResponse response) {
        UsuarioAutenticado destino = laboratorio.trocarEmpresa(usuario, empresaId);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(destino, null,
                List.of(new SimpleGrantedAuthority("ROLE_" + destino.perfil())));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        request.changeSessionId();
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
        return "redirect:/painel";
    }

    @GetMapping("/noticias")
    String noticias(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        model.addAttribute("noticias", laboratorio.noticias(usuario));
        return "noticias";
    }

    @GetMapping("/noticias/nova")
    String novaNoticia(Model model) {
        model.addAttribute("noticia", new Noticia(0, 0, "", "", "", "", null));
        return "noticia-form";
    }

    @GetMapping("/noticias/{id}/editar")
    String editarNoticia(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable long id, Model model) {
        model.addAttribute("noticia", laboratorio.noticia(usuario, id));
        return "noticia-form";
    }

    @PostMapping("/noticias/salvar")
    String salvarNoticia(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(required = false) Long id, @RequestParam String titulo,
            @RequestParam String conteudo, Model model, RedirectAttributes redirect) {
        try {
            laboratorio.salvarNoticia(usuario, id, titulo, conteudo);
        } catch (IllegalArgumentException exception) {
            model.addAttribute("erro", exception.getMessage());
            model.addAttribute("noticia", new Noticia(id == null ? 0 : id, usuario.empresaId(),
                    usuario.empresaNome(), titulo, conteudo, usuario.nome(), null));
            return "noticia-form";
        }
        redirect.addFlashAttribute("sucesso", "Notícia salva. Ela já está disponível no mural público.");
        return "redirect:/noticias";
    }

    @PostMapping("/noticias/{id}/excluir")
    String excluirNoticia(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable long id,
            RedirectAttributes redirect) {
        laboratorio.excluirNoticia(usuario, id);
        redirect.addFlashAttribute("sucesso", "Notícia excluída.");
        return "redirect:/noticias";
    }

    @GetMapping("/contas")
    String contas(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        model.addAttribute("contas", laboratorio.contas(usuario));
        return "contas";
    }

    @GetMapping("/contas/nova")
    String novaConta(Model model) {
        model.addAttribute("conta", new Conta(0, "", null, "PAGAR", LocalDate.now(), "PENDENTE"));
        return "conta-form";
    }

    @GetMapping("/contas/{id}/editar")
    String editarConta(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable long id, Model model) {
        model.addAttribute("conta", laboratorio.conta(usuario, id));
        return "conta-form";
    }

    @PostMapping("/contas/salvar")
    String salvarConta(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam(required = false) Long id, @RequestParam String descricao,
            @RequestParam BigDecimal valor, @RequestParam String tipo,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate vencimento,
            @RequestParam String situacao, Model model, RedirectAttributes redirect) {
        try {
            laboratorio.salvarConta(usuario, id, descricao, valor, tipo, vencimento, situacao);
        } catch (IllegalArgumentException exception) {
            model.addAttribute("erro", exception.getMessage());
            model.addAttribute("conta", new Conta(id == null ? 0 : id, descricao, valor, tipo, vencimento, situacao));
            return "conta-form";
        }
        redirect.addFlashAttribute("sucesso", "Conta salva com sucesso.");
        return "redirect:/contas";
    }

    @PostMapping("/contas/{id}/excluir")
    String excluirConta(@AuthenticationPrincipal UsuarioAutenticado usuario, @PathVariable long id,
            RedirectAttributes redirect) {
        laboratorio.excluirConta(usuario, id);
        redirect.addFlashAttribute("sucesso", "Conta excluída.");
        return "redirect:/contas";
    }

    @GetMapping("/usuarios")
    String usuarios(@AuthenticationPrincipal UsuarioAutenticado usuario, Model model) {
        model.addAttribute("membros", laboratorio.membros(usuario));
        return "usuarios";
    }

    @PostMapping("/usuarios")
    String cadastrarUsuario(@AuthenticationPrincipal UsuarioAutenticado usuario,
            @RequestParam String nome, @RequestParam String login, @RequestParam String senha,
            @RequestParam String perfil, Model model, RedirectAttributes redirect) {
        try {
            laboratorio.cadastrarUsuario(usuario, nome, login, senha, perfil);
        } catch (IllegalArgumentException exception) {
            model.addAttribute("erro", exception.getMessage());
            model.addAttribute("nomeInformado", nome);
            model.addAttribute("loginInformado", login);
            model.addAttribute("perfilInformado", perfil);
            return usuarios(usuario, model);
        }
        redirect.addFlashAttribute("sucesso", "Usuário cadastrado somente no curso atual.");
        return "redirect:/usuarios";
    }

    @RequestMapping("/acesso-negado")
    String acessoNegado(HttpServletResponse response, Model model) {
        response.setStatus(403);
        model.addAttribute("status", 403);
        model.addAttribute("mensagem", "Você não tem permissão para esta operação ou sua sessão de formulário expirou.");
        return "error";
    }
}
