package com.sistema.sistema_riego_inteligente.Entidad;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Entity
@Table(name = "clientes")
public class Cliente implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false, unique = true)
    @JsonIgnoreProperties({"hibernateLazyInitializer", "handler", "password"})
    private Usuario usuario;

    @Column(nullable = false, length = 100)
    private String nombres;

    @Column(nullable = false, length = 100)
    private String apellidos;

    @Column(name = "correo_personal", length = 100)
    private String correoPersonal;

    @Column(length = 20)
    private String telefono;

    @Column(nullable = false, length = 20)
    private String estado = "PENDIENTE";

    @Column(name = "fecha_registro")
    private LocalDateTime fechaRegistro;

    @Column(name = "fecha_activacion")
    private LocalDateTime fechaActivacion;

    @Column(name = "dispositivo_id", unique = true, length = 36)
    private String dispositivoId;

    @Column(name = "descripcion_servicio", length = 1000)
    private String descripcionServicio;

    @Column(name = "costo_sistema")
    private Double costoSistema = 120.0;

    @Column(name = "costo_sensores")
    private Double costoSensores = 20.0;

    @Column(name = "costo_componentes")
    private Double costoComponentes = 50.0;

    @Column(name = "costo_mensual")
    private Double costoMensual = 20.0;

    @Column(name = "fecha_proximo_pago")
    private LocalDateTime fechaProximoPago;

    @Column(name = "dias_gracia")
    private Integer diasGracia = 5;

    @PrePersist
    public void prePersist() {
        if (this.fechaRegistro == null) {
            this.fechaRegistro = LocalDateTime.now(ZoneId.of("America/Lima"));
        }
        if (this.dispositivoId == null) {
            this.dispositivoId = UUID.randomUUID().toString();
        }
    }

    public Cliente() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }

    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }

    public String getCorreoPersonal() { return correoPersonal; }
    public void setCorreoPersonal(String correoPersonal) { this.correoPersonal = correoPersonal; }

    public String getTelefono() { return telefono; }
    public void setTelefono(String telefono) { this.telefono = telefono; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    public LocalDateTime getFechaActivacion() { return fechaActivacion; }
    public void setFechaActivacion(LocalDateTime fechaActivacion) { this.fechaActivacion = fechaActivacion; }

    public String getDispositivoId() { return dispositivoId; }
    public void setDispositivoId(String dispositivoId) { this.dispositivoId = dispositivoId; }

    public String getDescripcionServicio() { return descripcionServicio; }
    public void setDescripcionServicio(String descripcionServicio) { this.descripcionServicio = descripcionServicio; }

    public Double getCostoSistema() { return costoSistema; }
    public void setCostoSistema(Double costoSistema) { this.costoSistema = costoSistema; }

    public Double getCostoSensores() { return costoSensores; }
    public void setCostoSensores(Double costoSensores) { this.costoSensores = costoSensores; }

    public Double getCostoComponentes() { return costoComponentes; }
    public void setCostoComponentes(Double costoComponentes) { this.costoComponentes = costoComponentes; }

    public Double getCostoMensual() { return costoMensual; }
    public void setCostoMensual(Double costoMensual) { this.costoMensual = costoMensual; }

    public LocalDateTime getFechaProximoPago() { return fechaProximoPago; }
    public void setFechaProximoPago(LocalDateTime fechaProximoPago) { this.fechaProximoPago = fechaProximoPago; }

    public Integer getDiasGracia() { return diasGracia; }
    public void setDiasGracia(Integer diasGracia) { this.diasGracia = diasGracia; }
}