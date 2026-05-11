-- =============================================================
-- MIGRACIÓN v2: Barreras por entrada y salida por dispositivo
-- Ejecutar en SSMS sobre la base de datos Datapark
-- =============================================================

-- PASO 1: Insertar las 4 barreras físicas en IOT_Barrera
-- (idempotente: no duplica si ya existen)

INSERT INTO IOT_Barrera (BarreraSeteo, EstadoBarrera, ComandoBarrera)
SELECT N'ENTRY1', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM IOT_Barrera WHERE BarreraSeteo = N'ENTRY1')

INSERT INTO IOT_Barrera (BarreraSeteo, EstadoBarrera, ComandoBarrera)
SELECT N'ENTRY2', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM IOT_Barrera WHERE BarreraSeteo = N'ENTRY2')

INSERT INTO IOT_Barrera (BarreraSeteo, EstadoBarrera, ComandoBarrera)
SELECT N'EXIT1', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM IOT_Barrera WHERE BarreraSeteo = N'EXIT1')

INSERT INTO IOT_Barrera (BarreraSeteo, EstadoBarrera, ComandoBarrera)
SELECT N'EXIT2', 1, 0
WHERE NOT EXISTS (SELECT 1 FROM IOT_Barrera WHERE BarreraSeteo = N'EXIT2')

-- Verificar IDs asignados (anota estos IDs, los necesitarás en la app)
SELECT ID, BarreraSeteo, EstadoBarrera FROM IOT_Barrera ORDER BY ID
GO

-- PASO 2: Agregar columnas IdBarreraEntrada e IdBarreraSalida a IOT_Dispositivos
IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'IOT_Dispositivos' AND COLUMN_NAME = 'IdBarreraEntrada'
)
    ALTER TABLE IOT_Dispositivos ADD IdBarreraEntrada INT NULL
GO

IF NOT EXISTS (
    SELECT 1 FROM INFORMATION_SCHEMA.COLUMNS
    WHERE TABLE_NAME = 'IOT_Dispositivos' AND COLUMN_NAME = 'IdBarreraSalida'
)
    ALTER TABLE IOT_Dispositivos ADD IdBarreraSalida INT NULL
GO

PRINT 'Migración v2 completada exitosamente'
