package com.sistema.sistema_riego_inteligente.servicios;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailtrapMailSender; // Sigue usando Mailtrap para el reset

    @Value("${brevo.api.key}")
    private String brevoApiKey;

    @Value("${brevo.sender.email}")
    private String brevoSenderEmail;

    @Value("${brevo.sender.name:Riego Inteligente}")
    private String brevoSenderName;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    private final RestClient restClient = RestClient.create();

    // --- Recuperación de contraseña (Mailtrap - simula dominio .riego) ---
    public void enviarResetPassword(String destinatario, String username, String link) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom("noreply@riego.local");
        msg.setTo(destinatario);
        msg.setSubject("Restablece tu contraseña · Riego Inteligente");
        msg.setText(
                "Hola " + username + ",\n\n" +
                "Recibimos una solicitud para restablecer tu contraseña.\n" +
                "Abre el siguiente enlace (válido por 30 minutos):\n\n" +
                link + "\n\n" +
                "Si no solicitaste esto, ignora este correo.\n\n" +
                "— Sistema de Riego Inteligente"
        );
        mailtrapMailSender.send(msg);
    }

    // --- Credenciales de acceso (Brevo - llega al correo real del cliente) ---
    public void enviarCredenciales(String destinatario, String nombres, String username, String password) {
        String htmlContent = String.format("""
                <html>
                <body>
                    <h2>¡Hola, %s!</h2>
                    <p>Tu sistema de riego ya está listo. Estas son tus credenciales de acceso:</p>
                    <ul>
                        <li><strong>Usuario:</strong> %s</li>
                        <li><strong>Contraseña temporal:</strong> %s</li>
                    </ul>
                    <p>Ingresa aquí: <a href="%s/login.html">%s/login.html</a></p>
                    <p>Por seguridad, cambia tu contraseña después del primer inicio de sesión.</p>
                    <br>
                    <p>— Sistema de Riego Inteligente</p>
                </body>
                </html>
                """, nombres, username, password, frontendUrl, frontendUrl);

        Map<String, Object> body = Map.of(
                "sender", Map.of("name", brevoSenderName, "email", brevoSenderEmail),
                "to", List.of(Map.of("email", destinatario, "name", nombres)),
                "subject", "Tus credenciales de acceso · Riego Inteligente",
                "htmlContent", htmlContent
        );

        restClient.post()
                .uri("https://api.brevo.com/v3/smtp/email")
                .header("api-key", brevoApiKey)
                .header("Content-Type", "application/json")
                .body(body)
                .retrieve()
                .toBodilessEntity();
    }
}