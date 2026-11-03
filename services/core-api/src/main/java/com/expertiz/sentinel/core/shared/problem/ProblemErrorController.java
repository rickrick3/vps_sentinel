package com.expertiz.sentinel.core.shared.problem;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Remplace la page d'erreur de Spring Boot (spec §11) : les erreurs survenues hors de Spring MVC (requête refusée par
 * le pare-feu de Spring Security, exception dans un filtre) sont rendues en Problem Details avec leur statut d'origine.
 * Appelé directement, sans erreur en cours, il répond 404.
 */
@Controller
@RequestMapping("${server.error.path:/error}")
class ProblemErrorController implements ErrorController {

    private final ProblemWriter problems;

    ProblemErrorController(ProblemWriter problems) {
        this.problems = problems;
    }

    @RequestMapping
    void error(HttpServletRequest request, HttpServletResponse response) throws IOException {
        ProblemType type = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE) instanceof Integer status
                ? ProblemType.forFrameworkStatus(status)
                : ProblemType.NOT_FOUND;
        problems.write(request, response, type, Problems.genericDetail(type));
    }
}
