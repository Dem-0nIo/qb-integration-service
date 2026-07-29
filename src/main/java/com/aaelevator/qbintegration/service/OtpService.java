package com.aaelevator.qbintegration.service;

import com.aaelevator.qbintegration.entity.Customer;
import com.aaelevator.qbintegration.entity.OtpToken;
import com.aaelevator.qbintegration.repository.CustomerRepository;
import com.aaelevator.qbintegration.repository.OtpTokenRepository;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Optional;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    @Autowired
    private OtpTokenService otpTokenService;

    private final OtpTokenRepository otpTokenRepository;
    private final CustomerRepository customerRepository;
    private final JwtService jwtService;
    private final JavaMailSender mailSender;
    private final String mailFrom;

    public OtpService(
            OtpTokenRepository otpTokenRepository,
            CustomerRepository customerRepository,
            JwtService jwtService,
            JavaMailSender mailSender,
            @Value("${spring.mail.username}") String mailFrom) {
        this.otpTokenRepository = otpTokenRepository;
        this.customerRepository = customerRepository;
        this.jwtService = jwtService;
        this.mailSender = mailSender;
        this.mailFrom = mailFrom;
    }


    public void requestOtp(String email, String channel) {

        // Verificar que el email existe en QB (soporta el campo email
        // multi-valor separado por ';' que exporta QuickBooks, ej.
        // "andorplaza@gmail.com; adiaz@pmasflorida.com")
        log.info("Checking email existence for: '{}'", email);
        boolean emailExists = findActiveCustomerByEmail(email).isPresent();
        log.info("Email exists: {}", emailExists);

        if (!emailExists) {
            //por seguridad responde igual aunque no exista
            log.warn("OTP requested for unknown email : {}", email);
            return;
        }

        if ("sms".equalsIgnoreCase(channel) || "whatsapp".equalsIgnoreCase(channel)) {

            String to = channel.equalsIgnoreCase("whatsapp") ? "whatsapp:+" : "+";
        } else {
            // Generar OTP y enviar por email
            String otpCode = generateOtpCode();
            otpTokenService.saveOtpToken(email, otpCode);

            sendOtpEmail(email, otpCode);
            log.info("OTP sent by email to: {}", email);
        }
    }

    @Transactional
    public Optional<String> verifyOtp(String email, String otpCode) {
        Optional<OtpToken> tokenOpt = otpTokenRepository.findTopByEmailAndUsedFalseAndExpiresAtAfterOrderByCreatedAtDesc(email, LocalDateTime.now());

        if (tokenOpt.isEmpty()) {
            log.warn("No valid OTP found for email : {}", email);
            return Optional.empty();
        }

        OtpToken token = tokenOpt.get();
        if (!token.getOtpCode().equals(otpCode)) {
            log.warn("Invalid OTP attempt for email : {}", email);
            return Optional.empty();
        }

        // Se marca como usado
        token.setUsed(true);
        otpTokenRepository.save(token);

        // Buscar la empresa QB asociada al email (mismo soporte multi-valor)
        String qbEmpresa = findActiveCustomerByEmail(email)
                .map(c -> c.getCompanyName() != null && !c.getCompanyName().isEmpty()
                        ? c.getCompanyName() : c.getFullName().split(":")[0].trim()).orElse(email);

        String jwt = jwtService.generateToken(email, qbEmpresa);
        log.info("OTP verified successfully for email: {}", email);
        return Optional.of(jwt);
    }

    /**
     * Busca un cliente activo cuyo campo email coincida con el email dado.
     *
     * El campo customers.email puede contener múltiples direcciones separadas
     * por ';' (tal como QuickBooks las exporta cuando un cliente tiene varios
     * contactos, ej. "andorplaza@gmail.com; adiaz@pmasflorida.com"). Una
     * comparación de igualdad exacta (customerRepository.existsByEmailAndIsActiveTrue)
     * nunca hace match en esos casos, bloqueando el login de esos clientes
     * aunque estén activos y el email sea correcto. Este método separa el
     * campo por ';', normaliza cada dirección (trim + lowercase) y compara
     * contra cada una individualmente.
     */
    private Optional<Customer> findActiveCustomerByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String target = email.trim().toLowerCase();

        return customerRepository.findAllByIsActiveTrue().stream()
                .filter(c -> c.getEmail() != null)
                .filter(c -> Arrays.stream(c.getEmail().split(";"))
                        .map(String::trim)
                        .map(String::toLowerCase)
                        .anyMatch(target::equals))
                .findFirst();
    }

    private String generateOtpCode() {
        int code = (int) (Math.random() * 900000) + 100000;
        return String.valueOf(code);
    }

    private void sendOtpEmail(String to, String otpCode) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
            helper.setFrom(mailFrom);
            // Destinatario fijo a propósito mientras el sistema está en
            // validación controlada con clientes reales — evita que cualquier
            // error de envío llegue a un cliente fuera de control. Cambiar
            // a helper.setTo(to) cuando se decida abrir el envío real.
            helper.setTo("davidmillan@outlook.com");
            helper.setSubject("A&A Elevator - Your access code");
            helper.setText(buildOtpEmailPlainText(otpCode), buildOtpEmailHtml(otpCode));
            mailSender.send(mimeMessage);
        } catch (MessagingException e) {
            throw new RuntimeException("Failed to send OTP email", e);
        }
    }

    private String buildOtpEmailPlainText(String otpCode) {
        return """
        A&A Elevator

        Your access code: %s

        This code expires in 10 minutes.

        If you did not request this code, you can safely ignore this email.

        A&A Elevator Client Portal - clients.aaelevator.net
        """.formatted(otpCode);
    }

    private String buildOtpEmailHtml(String otpCode) {
        return """
        <table cellpadding="0" cellspacing="0" style="width: 100%%; max-width: 480px; margin: 0 auto; background: #ffffff; border-radius: 12px; overflow: hidden; font-family: Arial, sans-serif;">
          <tr>
            <td style="background: linear-gradient(135deg, #659ad1, #32327f); background-color: #32327f; padding: 28px 32px;">
              <span style="color: #ffffff; font-size: 18px; font-weight: bold;">A&amp;A Elevator</span>
            </td>
          </tr>
          <tr>
            <td style="padding: 32px;">
              <p style="margin: 0 0 4px; font-size: 13px; color: #6b7280; text-transform: uppercase; letter-spacing: 0.5px;">Your access code</p>
              <p style="margin: 0 0 20px; font-size: 15px; color: #1B3D6F;">Use this code to sign in to your client portal.</p>
              <div style="background: #f3f6fb; border: 1px solid #dbe6f4; border-radius: 8px; padding: 18px 0; text-align: center; margin-bottom: 20px;">
                <span style="font-size: 32px; font-weight: bold; letter-spacing: 6px; color: #1B3D6F; font-family: monospace;">%s</span>
              </div>
              <p style="margin: 0 0 4px; font-size: 13px; color: #6b7280;">This code expires in 10 minutes.</p>
              <p style="margin: 0; font-size: 13px; color: #9ca3af;">If you did not request this code, you can safely ignore this email.</p>
            </td>
          </tr>
          <tr>
            <td style="padding: 16px 32px; border-top: 1px solid #eef1f5;">
              <span style="font-size: 12px; color: #9ca3af;">A&amp;A Elevator Client Portal &middot; clients.aaelevator.net</span>
            </td>
          </tr>
        </table>
        """.formatted(otpCode);
    }
}