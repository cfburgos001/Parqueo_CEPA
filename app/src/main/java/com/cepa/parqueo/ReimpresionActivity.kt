package com.cepa.parqueo

import android.content.Intent
import android.os.Bundle
import com.cepa.parqueo.databinding.ActivityReimpresionBinding

/**
 * Hub de Reimpresión: da acceso a reimprimir el ticket de entrada
 * (por si el QR físico está dañado) o la factura de pago (por si el
 * cliente dañó su comprobante). Ambas opciones buscan por placa.
 */
class ReimpresionActivity : DrawerActivity() {

    private lateinit var binding: ActivityReimpresionBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityReimpresionBinding.inflate(layoutInflater)
        setContentViewWithDrawer(binding.root, R.id.nav_reimpresion)
        supportActionBar?.title = "Reimpresión"

        binding.btnReimprimirTicket.setOnClickListener {
            startActivity(Intent(this, ReimprimirTicketActivity::class.java))
        }

        binding.btnReimprimirFactura.setOnClickListener {
            startActivity(Intent(this, ReimprimirFacturaActivity::class.java))
        }
    }
}