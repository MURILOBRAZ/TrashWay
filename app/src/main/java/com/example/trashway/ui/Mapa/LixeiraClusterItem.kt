package com.example.trashway.ui.Mapa

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.core.content.ContextCompat
import com.example.trashway.R
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.maps.android.clustering.ClusterItem
import com.google.maps.android.clustering.ClusterManager
import com.google.maps.android.clustering.view.DefaultClusterRenderer

// Lixeira como item do agrupamento de marcadores (android-maps-utils)
class LixeiraClusterItem(val lixeira: Lixeira) : ClusterItem {
    override fun getPosition(): LatLng = lixeira.latLng
    override fun getTitle(): String = lixeira.nome
    override fun getSnippet(): String = lixeira.local
    override fun getZIndex(): Float = 0f
}

// Desenha cada lixeira com o marcador próprio e os grupos na cor da marca
class LixeiraClusterRenderer(
    context: Context,
    map: GoogleMap,
    clusterManager: ClusterManager<LixeiraClusterItem>
) : DefaultClusterRenderer<LixeiraClusterItem>(context, map, clusterManager) {

    private val icone: BitmapDescriptor = vetorParaBitmap(context, R.drawable.ic_marcador_lixeira)
    private val corGrupo = ContextCompat.getColor(context, R.color.verde_marca)

    override fun onBeforeClusterItemRendered(item: LixeiraClusterItem, markerOptions: MarkerOptions) {
        markerOptions.icon(icone).title(item.title).snippet(item.snippet).anchor(0.5f, 0.5f)
    }

    override fun onClusterItemUpdated(item: LixeiraClusterItem, marker: Marker) {
        marker.setIcon(icone)
        marker.title = item.title
        marker.snippet = item.snippet
    }

    override fun getColor(clusterSize: Int): Int = corGrupo

    private fun vetorParaBitmap(context: Context, resId: Int): BitmapDescriptor {
        val drawable = ContextCompat.getDrawable(context, resId)!!
        val bitmap = Bitmap.createBitmap(
            drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888
        )
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        return BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}
