-- =============================================================
-- MIGRACIÓN v3: Corregir trigger y SP para usar barreras por dispositivo
-- Ejecutar en SSMS sobre la base de datos Datapark
-- REQUISITO: Haber ejecutado v1 y v2 primero
-- =============================================================

-- ============================================================
-- FIX 1: Trigger trg_IOT_Vehiculos_AbrirBarrera
-- Antes: siempre actualizaba IOT_Barrera WHERE ID = 1
-- Ahora: busca IdBarreraEntrada / IdBarreraSalida del dispositivo
--        y cae en ID=1 si el dispositivo no tiene barrera asignada
-- ============================================================
ALTER TRIGGER [dbo].[trg_IOT_Vehiculos_AbrirBarrera]
ON [dbo].[IOT_Vehiculos]
AFTER INSERT, UPDATE
AS
BEGIN
    SET NOCOUNT ON;

    -- Abrir barrera de ENTRADA para los dispositivos involucrados
    UPDATE IOT_Barrera
    SET
        EstadoBarrera            = 1,
        ComandoBarrera           = 1,
        FechaUltimaActualizacion = GETDATE()
    WHERE ID IN (
        SELECT ISNULL(d.IdBarreraEntrada, 1)
        FROM inserted i
        LEFT JOIN deleted  del ON i.Id = del.Id
        LEFT JOIN IOT_Dispositivos d ON d.IdDispositivo = i.IdDispositivoEntrada
        WHERE i.bitEntry = 1
          AND (del.bitEntry IS NULL OR del.bitEntry = 0)
    );

    -- Abrir barrera de SALIDA para los dispositivos involucrados
    UPDATE IOT_Barrera
    SET
        EstadoBarrera            = 1,
        ComandoBarrera           = 1,
        FechaUltimaActualizacion = GETDATE()
    WHERE ID IN (
        SELECT ISNULL(d.IdBarreraSalida, 1)
        FROM inserted i
        LEFT JOIN deleted  del ON i.Id = del.Id
        LEFT JOIN IOT_Dispositivos d ON d.IdDispositivo = i.IdDispositivoSalida
        WHERE i.bitExit = 1
          AND (del.bitExit IS NULL OR del.bitExit = 0)
    );
END
GO

-- ============================================================
-- FIX 2: IOT_sp_RegistrarAperturaManual
-- Agrega @IdBarrera INT = 1 y reemplaza WHERE ID = 1
-- ============================================================
ALTER PROCEDURE [dbo].[IOT_sp_RegistrarAperturaManual]
    @IdDispositivo NVARCHAR(50),
    @TipoApertura  NVARCHAR(20),  -- 'ENTRADA' o 'SALIDA'
    @IdOperador    INT,
    @Motivo        NVARCHAR(255) = NULL,
    @IdBarrera     INT = 1        -- barrera a abrir (default 1 para compatibilidad)
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @Id             INT;
    DECLARE @NombreOperador NVARCHAR(200);
    DECLARE @IdTipoLog      INT;
    DECLARE @DatosLog       NVARCHAR(500);

    BEGIN TRANSACTION;

    BEGIN TRY
        IF @TipoApertura NOT IN ('ENTRADA', 'SALIDA')
        BEGIN
            ROLLBACK TRANSACTION;
            SELECT 0 AS Exitoso, 'TipoApertura debe ser ENTRADA o SALIDA' AS Mensaje, NULL AS Id;
            RETURN;
        END

        SELECT @NombreOperador = CONCAT(Nombre, ' ', Apellido)
        FROM dbo.IOT_Operadores
        WHERE Id = @IdOperador;

        INSERT INTO dbo.IOT_AperturaManual
            (FechaApertura, IdDispositivo, TipoApertura, IdOperador, NombreOperador, Motivo)
        VALUES
            (GETDATE(), @IdDispositivo, @TipoApertura, @IdOperador, @NombreOperador, @Motivo);

        SET @Id = SCOPE_IDENTITY();

        SET @IdTipoLog = CASE WHEN @TipoApertura = 'ENTRADA' THEN 3 ELSE 4 END;

        SET @DatosLog = 'Apertura manual de barrera - ' + @TipoApertura +
                        ' - Barrera: ' + CAST(@IdBarrera AS NVARCHAR(10)) +
                        ' - Operador: ' + ISNULL(@NombreOperador, 'Desconocido') +
                        CASE WHEN @Motivo IS NOT NULL THEN ' - Motivo: ' + @Motivo ELSE '' END;

        EXEC dbo.IOT_sp_RegistroLog
            @IdTipoLog       = @IdTipoLog,
            @Placa           = NULL,
            @IdDispositivo   = @IdDispositivo,
            @DatosAdicionales = @DatosLog;

        -- Abrir la barrera correcta
        UPDATE dbo.IOT_Barrera
        SET
            ComandoBarrera           = 1,
            FechaUltimaActualizacion = GETDATE()
        WHERE ID = @IdBarrera;

        COMMIT TRANSACTION;

        SELECT
            1          AS Exitoso,
            'Apertura manual registrada correctamente - Barrera ID: ' + CAST(@IdBarrera AS NVARCHAR(10)) AS Mensaje,
            @Id        AS Id;

    END TRY
    BEGIN CATCH
        ROLLBACK TRANSACTION;
        SELECT 0 AS Exitoso, ERROR_MESSAGE() AS Mensaje, NULL AS Id;
    END CATCH
END
GO

PRINT 'Migración v3 completada: trigger y SP corregidos para usar barreras por dispositivo'
