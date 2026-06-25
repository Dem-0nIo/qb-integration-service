package com.aaelevator.qbintegration.service;

import com.aaelevator.qbintegration.entity.OtpToken;
import com.aaelevator.qbintegration.repository.CustomerRepository;
import com.aaelevator.qbintegration.repository.OtpTokenRepository;
import com.twilio.Twilio;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class OtpService {

    private static final Logger log = LoggerFactory.getLogger(OtpService.class);

    private final OtpTokenRepository otpTokenRepository;
    private final CustomerRepository customerRepository;
    private final JwtService jwtService;
    private final JavaMailSender mailSender;

    private final String twilioAccountSid;
    private final String twilioAuthToken;
    private final String twilioVerifyServiceSid;
    private final String mailFrom;

    public OtpService(
            OtpTokenRepository otpTokenRepository,
            CustomerRepository customerRepository,
            JwtService jwtService,
            JavaMailSender mailSender,
            @Value("${twilio.account.sid}") String twilioAccountSid,
            @Value("${twilio.auth.token}") String twilioAuthToken,
            @Value("${twilio.verify.service.sid") String twilioVerifyServiceSid,
            @Value("${spring.mail.username") String mailFrom) {
                this.otpTokenRepository = otpTokenRepository;
                this.customerRepository = customerRepository;
                this.jwtService = jwtService;
                this.mailSender = mailSender;
                this.twilioAccountSid = twilioAccountSid;
                this.twilioAuthToken = twilioAuthToken;
                this.twilioVerifyServiceSid = twilioVerifyServiceSid;
                this.mailFrom = mailFrom;
                Twilio.init(twilioAccountSid, twilioAuthToken);
            }

    @Transactional
    public void requestOtp(String email, String channel) {

        //1. Verificar que el email existe en QB
        boolean emailExists = customerRepository.existsByEmail(email);

        if (emailExists) {
            //por seguridad responde igual aunque no exista
            log.warn("OTP requested for unknown email : {}", email);
            return;
        }

        //2. Limpiar OTPs usados
        otpTokenRepository.deleteByEmailAndUsedTrue(email);

        if ("sms".equalsIgnoreCase(channel) || "whatsapp".equalsIgnoreCase(channel)) {
            //3. Delegar a Twilio Verify para SMS/WhatsApp
            String to = channel.equalsIgnoreCase("whatsapp") ? "whatsapp:+" : "+";
            log.info("OTP via Twilio Verify requested for email : {}", email);
            // *****Nota: Twilio Verify maneja internamente el OTP - no se guarda en DB del sistema
            // Para SMS/WhatsApp se usa el número de teléfono del cliente, no el email
            // Esta rama se activará cuando agregue el teléfono del cliente
        } else {
            // 3b. Generar OTP y enviar por email
            String otpCode = generateOtpCode();

            OtpToken token = new OtpToken();
            token.setEmail(email);
            token.setOtpCode(otpCode);
            token.setExpiresAt(LocalDateTime.now().plusMinutes(10));
            token.setUsed(false);
            otpTokenRepository.save(token);

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

        // Buscar la empresa QB asociada al email
        String qbEmpresa = customerRepository.findFirstByEmail(email)
                            .map(c -> c.getCompanyName() != null && !c.getCompanyName().isEmpty()
                                    ? c.getCompanyName() : c.getFullName().split(":")[0].trim()).orElse(email);

        String jwt = jwtService.generateToken(email, qbEmpresa);
        log.info("OTP verified successfully for email: {}", email);
        return Optional.of(jwt);
    }

    private String generateOtpCode() {
        int code = (int) (Math.random() * 900000) + 100000;
        return String.valueOf(code);
    }

    private void sendOtpEmail(String to, String otpCode) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom("test@aaelevator.net");
        //message.setTo(to);
        message.setTo("davidmillan@outlook.com");
        message.setSubject("A&A Elevator - Código de acceso");
        message.setText("Su código de acceso al portal de A&A Elevator es: " + otpCode +
                "\n\nEste código expira en 10 minutos." +
                "\n\nSi no solicitó este código, ignore este mensaje.");
        mailSender.send(message);
    }
}
