package com.cepa.parqueo

import android.content.Intent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.navigation.NavigationView

/**
 * Activity base que envuelve el contenido de cada pantalla en un
 * DrawerLayout + NavigationView. Una pantalla se engancha con:
 *
 *   class XActivity : DrawerActivity() {
 *       override fun onCreate(...) {
 *           binding = ActivityXBinding.inflate(layoutInflater)   // igual que siempre
 *           setContentViewWithDrawer(binding.root, R.id.nav_...)  // en vez de setContentView(binding.root)
 *       }
 *   }
 *
 * No contiene lógica de negocio de ninguna pantalla; solo navegación de menú y cierre de sesión.
 */
abstract class DrawerActivity : AppCompatActivity() {

    protected lateinit var drawerLayout: DrawerLayout
        private set
    protected lateinit var toolbar: MaterialToolbar
        private set
    protected lateinit var navigationView: NavigationView
        private set

    private val session by lazy { getSharedPreferences("ParkingSession", MODE_PRIVATE) }

    protected val userType: UserType
        get() = runCatching {
            UserType.valueOf(session.getString("userType", null) ?: "OPERADOR")
        }.getOrDefault(UserType.OPERADOR)

    /** Uso normal: la pantalla infla su ViewBinding y pasa binding.root aquí. */
    protected fun setContentViewWithDrawer(contentView: View, @IdRes checkedItem: Int = 0) {
        val root = layoutInflater.inflate(R.layout.layout_app_drawer, null) as DrawerLayout
        root.findViewById<FrameLayout>(R.id.drawer_content_frame).addView(
            contentView,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        super.setContentView(root)
        setupDrawer(root, checkedItem)
    }

    /** Alternativa para pantallas sin ViewBinding. */
    protected fun setContentViewWithDrawer(@LayoutRes contentLayoutId: Int, @IdRes checkedItem: Int = 0) {
        val root = layoutInflater.inflate(R.layout.layout_app_drawer, null) as DrawerLayout
        layoutInflater.inflate(contentLayoutId, root.findViewById(R.id.drawer_content_frame), true)
        super.setContentView(root)
        setupDrawer(root, checkedItem)
    }

    private fun setupDrawer(root: DrawerLayout, @IdRes checkedItem: Int) {
        drawerLayout = root
        toolbar = root.findViewById(R.id.drawer_toolbar)
        navigationView = root.findViewById(R.id.drawer_navigation)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(true)

        val toggle = ActionBarDrawerToggle(
            this, drawerLayout, toolbar, R.string.drawer_open, R.string.drawer_close
        )
        drawerLayout.addDrawerListener(toggle)
        toggle.syncState()

        bindHeader()
        applyRolePermissions()
        if (checkedItem != 0) navigationView.setCheckedItem(checkedItem)

        navigationView.setNavigationItemSelectedListener { item ->
            drawerLayout.closeDrawer(GravityCompat.START)
            val alreadyHere = item.itemId == navigationView.checkedItem?.itemId
            if (!alreadyHere || item.itemId == R.id.nav_cerrar_sesion) {
                onDrawerItemSelected(item.itemId)
            }
            true
        }
    }

    private fun bindHeader() {
        val header = navigationView.getHeaderView(0)
        header.findViewById<TextView>(R.id.nav_header_name).text =
            session.getString("nombre_completo", "Operador")
        header.findViewById<TextView>(R.id.nav_header_role).text =
            session.getString("userType", "").orEmpty()
    }

    /** Mantenimiento solo ADMINISTRADOR; Ingreso oculto para CAJA (UserPermissions.kt). */
    private fun applyRolePermissions() {
        navigationView.menu.findItem(R.id.nav_mantenimiento)?.isVisible =
            userType.canAccessMaintenance()
        navigationView.menu.findItem(R.id.nav_ingreso)?.isVisible =
            userType.canAccessEntry()
    }

    /** Navegación por defecto. Una pantalla puede sobreescribirla para casos especiales. */
    protected open fun onDrawerItemSelected(@IdRes itemId: Int) {
        if (itemId == R.id.nav_cerrar_sesion) {
            confirmLogout()
            return
        }
        val target: Class<*> = when (itemId) {
            R.id.nav_dashboard -> HomeActivity::class.java
            R.id.nav_ingreso -> IngresoVehiculoActivity::class.java
            R.id.nav_salida -> SalidaVehiculoActivity::class.java
            R.id.nav_apertura_cierre -> AperturaCierreActivity::class.java
            R.id.nav_reimpresion -> ReimpresionActivity::class.java
            R.id.nav_ticket_perdido -> TicketPerdidoActivity::class.java
            R.id.nav_mantenimiento -> MantenimientoActivity::class.java
            else -> return
        }
        if (target == this::class.java) return
        startActivity(Intent(this, target).putExtra("USER_TYPE", userType.name))
        finish()   // no apilar pantallas al moverse entre secciones con el drawer
    }

    protected fun confirmLogout() {
        AlertDialog.Builder(this)
            .setTitle("Cerrar Sesión")
            .setMessage("¿Está seguro que desea cerrar sesión?")
            .setPositiveButton("Sí") { _, _ -> doLogout() }
            .setNegativeButton("No", null)
            .show()
    }

    private fun doLogout() {
        session.edit().clear().apply()
        com.cepa.parqueo.database.SiteConfigCache.clear()
        startActivity(
            Intent(this, LoginActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        )
        finish()
    }

    override fun onBackPressed() {
        if (::drawerLayout.isInitialized && drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }
}
