package com.aaelevator.qbintegration.loadtest;

import com.aaelevator.qbintegration.service.JwtService;

import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utilidad standalone para generar JWTs válidos de prueba, sin levantar
 * el contexto de Spring ni pasar por el flujo real de OTP por email.
 *
 * IMPORTANTE: JwtAuthenticationFilter + SecurityConfig exigen que el email
 * del claim "sub" corresponda a un cliente REAL y ACTIVO en la tabla
 * customers (SecurityConfig exige hasRole("CLIENT") para /api/client/**,
 * y ese rol solo se asigna si el filtro encuentra ese email como cliente
 * activo). Un email inventado produce un JWT con firma válida pero SIN
 * autenticación real, y SecurityConfig responde 404 (no 401) para
 * unauthenticated requests, vía authenticationEntryPoint personalizado.
 *
 * Por eso este generador ahora recibe pares "email:qbEmpresa" reales,
 * en vez de inventar un email por compañía.
 *
 * USO (desde IntelliJ: click derecho -> Run 'TokenGenerator.main()',
 * ajustando Program Arguments):
 *
 *   "<jwt.secret>" <jwt.expiration.hours> <archivo_salida.json> <email1:Empresa1> <email2:Empresa2> ...
 *
 * Ejemplo:
 *   "mi-secreto-local-con espacio-si-aplica" 8 tokens.json "bohemecondo@yahoo.com:Boheme Condo" "theedgewaterarms@gmail.com:Edgewater Arms"
 *
 * Si el secreto tiene espacios, ponlo entre comillas como UN SOLO argumento
 * en el campo Program Arguments de IntelliJ (que sí respeta comillas).
 *
 * Para obtener pares email:empresa reales y activos, corre en tu DB local:
 *   SELECT c.email, cm.qb_empresa
 *   FROM customers c
 *   JOIN customer_mapping cm ON cm.qb_empresa =
 *     COALESCE(NULLIF(c.company_name, ''), SUBSTRING_INDEX(c.full_name, ':', 1))
 *   WHERE c.is_active = 1;
 */
public class TokenGenerator {

    public static void main(String[] args) throws IOException {
        if (args.length < 4) {
            System.err.println("Uso: TokenGenerator <jwt.secret> <expiration.hours> <output.json> <email1:Empresa1> [email2:Empresa2 ...]");
            System.err.println("Ejemplo: TokenGenerator mi-secreto-local 8 tokens.json \"bohemecondo@yahoo.com:Boheme Condo\"");
            System.exit(1);
        }

        String secret = args[0];
        int expirationHours = Integer.parseInt(args[1]);
        String outputPath = args[2];
        List<String> pairs = Arrays.asList(args).subList(3, args.length);

        JwtService jwtService = new JwtService(secret, expirationHours);

        List<String[]> parsed = new ArrayList<>();
        for (String pair : pairs) {
            int idx = pair.indexOf(':');
            if (idx < 0) {
                System.err.println("Formato inválido, se esperaba 'email:Empresa': " + pair);
                System.exit(1);
            }
            String email = pair.substring(0, idx).trim();
            String company = pair.substring(idx + 1).trim();
            parsed.add(new String[]{email, company});
        }

        StringBuilder json = new StringBuilder("[\n");
        for (int i = 0; i < parsed.size(); i++) {
            String email = parsed.get(i)[0];
            String company = parsed.get(i)[1];
            String token = jwtService.generateToken(email, company);

            json.append("  {\"qbEmpresa\": \"").append(escapeJson(company)).append("\", ")
                    .append("\"email\": \"").append(escapeJson(email)).append("\", ")
                    .append("\"token\": \"").append(token).append("\"}");
            if (i < parsed.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("]\n");

        try (FileWriter writer = new FileWriter(outputPath)) {
            writer.write(json.toString());
        }

        System.out.println("Generados " + parsed.size() + " tokens en " + outputPath);
        System.out.println("Expiran en " + expirationHours + " horas — regenera si el test de carga tarda más que eso.");
    }

    private static String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}