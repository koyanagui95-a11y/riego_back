package com.sistema.sistema_riego_inteligente.servicios;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailtrapMailSender; // Para .riego (simulación)

    @Autowired
    @Qualifier("realMailSender")
    private JavaMailSender realMailSender; // Para correo personal (real)

    @Value("${app.mail.from:noreply@riego.local}")
    private String remitente;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    // Recuperación de contraseña → Mailtrap (simula dominio .riego)
    public void enviarResetPassword(String destinatario, String username, String link) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(remitente);
        msg.setTo(destinatario);
        msg.setSubject("Restablece tu contraseña · Riego Inteligente");
        msg.setText("Hola " + username + ",\n\n" + "...");
        mailtrapMailSender.send(msg);
    }

    // Credenciales → SMTP real (llega al correo personal de verdad)
    public void enviarCredenciales(String destinatario, String nombres, String username, String password) {
        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setFrom(remitente);
        msg.setTo(destinatario); // correoPersonal real
        msg.setSubject("Tus credenciales · Riego Inteligente");
        msg.setText("Hola " + nombres + ",\n\nUsuario: " + username + "\nContraseña: " + password + "\n\n...");
        realMailSender.send(msg); // ← envía por Gmail/SendGrid, no por Mailtrap
    }
}