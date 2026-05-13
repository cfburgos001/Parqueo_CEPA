-- =========================================================
-- MIGRACION: Soporte Multi-Barrera
-- Base de datos: Datapark
-- Ejecutar con usuario que tenga permisos ALTER TABLE / ALTER PROC
-- =========================================================

-- 1. Agregar columna IdBarreraAsignada a IOT_Dispositivos
IF NOT EXISTS (
    SELECT 1 FROM sys.columns
    WHERE object_id = OBJECT_ID('dbo.IOT_Dispositivos')
      AND name = 'IdBarreraAsignada'
)
BEGIN
    ALTER TABLE dbo.IOT_Dispositivos
    ADD IdBarreraAsignada INT NULL;
    PRINT 'Columna IdBarreraAsignada agregada a IOT_Dispositivos';
END
ELSE
    PRINT 'Columna IdBarreraAsignada ya existe (sin cambios)';
GO

-- 2. SP nuevo: Listar todas las barreras
IF OBJECT_ID('dbo.IOT_sp_ListarBarreras', 'P') IS NOT NULL
    DROP PROCEDURE dbo.IOT_sp_ListarBarreras;
GO
CREATE PROCEDURE dbo.IOT_sp_ListarBarreras
AS
BEGIN
    SET NOCOUNT ON;
    SELECT
        ID,
        BarreraSeteo,
        EstadoBarrera,
        ComandoBarrera,
        FechaUltimaActualizacion
    FROM dbo.IOT_Barrera
    ORDER BY ID;
END
GO
PRINT 'SP IOT_sp_ListarBarreras creado';
GO

-- 3. Modificar IOT_sp_EjecutarAperturaManualConLog: agrega @IdBarrera INT = 1
ALTER PROCEDURE dbo.IOT_sp_EjecutarAperturaManualConLog
    @IdTipoLog      INT,
    @IdOperador     INT,
    @NombreOperador NVARCHAR(200),
    @IdDispositivo  NVARCHAR(50),
    @Contexto       VARCHAR(20),
    @IdBarrera      INT = 1
AS
BEGIN
    SET NOCOUNT ON;

    DECLARE @DescripcionLog NVARCHAR(255);
    DECLARE @DatosLog       NVARCHAR(500);

    BEGIN TRANSACTION;

    BEGIN TRY
        SELECT @DescripcionLog = Descripcion
        FROM IOT_TiposLogs
        WHERE Id = @IdTipoLog AND Activo = 1;

        IF @DescripcionLog IS NULL
        BEGIN
            ROLLBACK TRANSACTION;
            SELECT 0 AS Exitoso, 'Tipo de log no encontrado o inactivo' AS Mensaje;
            RETURN;
        END

        UPDATE dbo.IOT_Barrera
        SET
            ComandoBarrera           = 1,
            EstadoBarrera            = 1,
            FechaUltimaActualizacion = GETDATE()
        WHERE ID = @IdBarrera;

        SET @DatosLog = @DescripcionLog
                      + ' - Operador: '  + ISNULL(@NombreOperador, 'Desconocido')
                      + ' (ID: '         + CAST(@IdOperador AS VARCHAR(10)) + ')'
                      + ' - '            + @Contexto
                      + ' - Barrera: '   + CAST(@IdBarrera  AS VARCHAR(10));

        INSERT INTO dbo.IOT_Logs (IdTipoLog, Placa, Datos, IdDispositivo, FechaEvento)
        VALUES (@IdTipoLog, NULL, @DatosLog, @IdDispositivo, GETDATE());

        COMMIT TRANSACTION;

        SELECT 1 AS Exitoso,
               'Barrera ' + CAST(@IdBarrera AS VARCHAR(10)) + ' abierta - ' + @DescripcionLog AS Mensaje;

    END TRY
    BEGIN CATCH
        ROLLBACK TRANSACTION;
        SELECT 0 AS Exitoso, 'Error: ' + ERROR_MESSAGE() AS Mensaje;
    END CATCH
END
GO
PRINT 'SP IOT_sp_EjecutarAperturaManualConLog modificado';
GO

-- 4. Modificar IOT_sp_CerrarBarreraManual: agrega @IdBarrera INT = 1
ALTER PROCEDURE dbo.IOT_sp_CerrarBarreraManual
    @IdBarrera INT = 1
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE dbo.IOT_Barrera
    SET
        ComandoBarrera           = 0,
        FechaUltimaActualizacion = GETDATE()
    WHERE ID = @IdBarrera;

    SELECT
        'Comando de cierre enviado' AS Mensaje,
        EstadoBarrera,
        ComandoBarrera,
        FechaUltimaActualizacion
    FROM dbo.IOT_Barrera
    WHERE ID = @IdBarrera;
END
GO
PRINT 'SP IOT_sp_CerrarBarreraManual modificado';
GO

-- 5. Modificar IOT_sp_ResetearBarrera: agrega @IdBarrera INT = 1
ALTER PROCEDURE dbo.IOT_sp_ResetearBarrera
    @IdBarrera INT = 1
AS
BEGIN
    SET NOCOUNT ON;

    UPDATE dbo.IOT_Barrera
    SET
        EstadoBarrera            = 0,
        ComandoBarrera           = 0,
        FechaUltimaActualizacion = GETDATE()
    WHERE ID = @IdBarrera;

    SELECT
        'Barrera reseteada correctamente' AS Mensaje,
        EstadoBarrera,
        ComandoBarrera,
        FechaUltimaActualizacion
    FROM dbo.IOT_Barrera
    WHERE ID = @IdBarrera;
END
GO
PRINT 'SP IOT_sp_ResetearBarrera modificado';
GO

PRINT '';
PRINT '=========================================';
PRINT 'Migracion multi-barrera completada OK';
PRINT '=========================================';
GO
