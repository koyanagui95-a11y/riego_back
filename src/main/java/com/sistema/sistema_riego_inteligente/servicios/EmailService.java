package com.sistema.sistema_riego_inteligente.servicios;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailtrapMailSender;

    @Value("${brevo.api.key}")
    private String brevoApiKey;

    @Value("${brevo.sender.email}")
    private String brevoSenderEmail;

    @Value("${brevo.sender.name:Riego Inteligente}")
    private String brevoSenderName;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Value("${app.mail.from:noreply@riego.local}")
    private String mailFrom;

    private final RestClient restClient = RestClient.create();

    public void enviarResetPassword(String destinatario, String username, String link) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(mailFrom);
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

        enviarCorreoBrevo(destinatario, nombres, "Tus credenciales de acceso · Riego Inteligente", htmlContent);
    }

    @Async
    public void enviarNotificacionEstadoServicio(String destinatario, String nombres, boolean suspendido, String motivo) {
        if (destinatario == null || destinatario.isBlank()) return;

        String asunto = suspendido
                ? "Aviso importante: Servicio suspendido · Riego Inteligente"
                : "Servicio reactivado · Riego Inteligente";

        String detalleMotivo = (motivo != null && !motivo.isBlank())
                ? motivo
                : "Falta de pago o revisión administrativa.";

        String htmlContent = suspendido
                ? String.format("""
                <html>
                <body>
                    <h2>Hola, %s</h2>
                    <p>Te informamos que tu servicio de monitoreo y control de riego ha sido <strong>SUSPENDIDO</strong>.</p>
                    <p><strong>Motivo:</strong> %s</p>
                    <p>Comunícate con el administrador para regularizar tu estado y reactivar el acceso.</p>
                    <br>
                    <p>— Sistema de Riego Inteligente</p>
                </body>
                </html>
                """, nombres, detalleMotivo)
                : String.format("""
                <html>
                <body>
                    <h2>¡Hola de nuevo, %s!</h2>
                    <p>Tu servicio de Riego Inteligente ha sido <strong>REACTIVADO</strong> correctamente.</p>
                    <p>Ya puedes ingresar nuevamente a tu panel: <a href="%s/login.html">%s/login.html</a></p>
                    <br>
                    <p>— Sistema de Riego Inteligente</p>
                </body>
                </html>
                """, nombres, frontendUrl, frontendUrl);

        try {
            enviarCorreoBrevo(destinatario, nombres, asunto, htmlContent);
        } catch (Exception e) {
            System.err.println("[BREVO] Error enviando notificación de estado: " + e.getMessage());
        }
    }

    private void enviarCorreoBrevo(String destinatario, String nombres, String subject, String htmlContent) {
        Map<String, Object> body = Map.of(
                "sender", Map.of("name", brevoSenderName, "email", brevoSenderEmail),
                "to", List.of(Map.of("email", destinatario, "name", nombres)),
                "subject", subject,
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