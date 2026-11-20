package com.sistema.sistema_riego_inteligente.servicios;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${app.mail.from:noreply@riego.local}")
    private String remitente;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public void enviarResetPassword(String destinatario, String username, String link) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(remitente);
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
        mailSender.send(msg);
    }

    public void enviarCredenciales(String destinatario, String nombres, String username, String password) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(remitente);
        msg.setTo(destinatario);
        msg.setSubject("Tus credenciales · Riego Inteligente");
        msg.setText(
                "Hola " + nombres + ",\n\n" +
                "Tu sistema de riego ya está listo. Estas son tus credenciales de acceso:\n\n" +
                "Usuario:    " + username + "\n" +
                "Contraseña: " + password + "\n\n" +
                "Ingresa aquí: " + frontendUrl + "/login.html\n\n" +
                "Por seguridad, cambia tu contraseña después del primer inicio de sesión.\n\n" +
                "— Sistema de Riego Inteligente"
        );
        mailSender.send(msg);
    }
}