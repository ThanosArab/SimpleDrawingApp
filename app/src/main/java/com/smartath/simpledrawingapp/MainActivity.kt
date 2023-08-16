package com.smartath.simpledrawingapp

import android.Manifest
import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.skydoves.colorpickerview.ColorPickerView
import com.skydoves.colorpickerview.listeners.ColorEnvelopeListener
import com.skydoves.colorpickerview.preference.ColorPickerPreferenceManager
import com.skydoves.colorpickerview.sliders.AlphaSlideBar
import com.skydoves.colorpickerview.sliders.BrightnessSlideBar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File


class MainActivity : AppCompatActivity() {
    private var drawingView: DrawingView? = null

    private var brushBtn: ImageButton? = null
    private var colorLayout: LinearLayout? = null
    private var addLayout: LinearLayout? = null
    private var colorBtn: ImageButton? = null
    private var randomBtn: ImageButton? = null

    private var uploadImageBtn: ImageButton? = null
    private var saveImageBtn: ImageButton? = null
    private var shareImageBtn: ImageButton? = null

    private var customProgressDialog: Dialog? = null

    private var stringColor: String = ""

    private val openGalleryLauncher: ActivityResultLauncher<Intent> = registerForActivityResult(ActivityResultContracts.StartActivityForResult()){
        result ->
        if (result.resultCode == RESULT_OK && result.data != null){
            val backgroundImage: ImageView = findViewById(R.id.backgroundImage)
            backgroundImage.setImageURI(result.data?.data)
        }
    }

    private val requestPermission: ActivityResultLauncher<Array<String>> = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){
        permissions ->
        permissions.entries.forEach{
            val permissionName = it.key
            val isGranted = it.value

            if(isGranted){
                Toast.makeText(this, "Permission granted! Now you can read the storage files.", Toast.LENGTH_LONG).show()

                val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                openGalleryLauncher.launch(pickIntent)
            }
            else{
                if (permissionName == Manifest.permission.READ_EXTERNAL_STORAGE){
                    Toast.makeText(this, "Permission denied!", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        drawingView = findViewById(R.id.drawingView)
        drawingView?.setBrushSize(20.toFloat())

        colorLayout = findViewById(R.id.colorLayout)
        addLayout = findViewById(R.id.addLayout)
        randomBtn = findViewById(R.id.randomBtn)

        uploadImageBtn = findViewById(R.id.uploadImageBtn)
        saveImageBtn = findViewById(R.id.saveImageBtn)
        shareImageBtn = findViewById(R.id.shareImageBtn)

        brushBtn = findViewById(R.id.brushBtn)
        brushBtn?.setOnClickListener {
            drawingView?.getBrushSize()
            showBrushChooserDialog()
        }

        uploadImageBtn?.setOnClickListener {
            uploadImage()
        }

        saveImageBtn?.setOnClickListener {
            showProgressDialog()
            lifecycleScope.launch {
                val drawing: FrameLayout = findViewById(R.id.drawingViewContainer)
                saveBitmap(drawing)
            }
        }

        shareImageBtn?.setOnClickListener {
            lifecycleScope.launch {
                val drawing: FrameLayout = findViewById(R.id.drawingViewContainer)
                shareBitmap(getBitmapFromView(drawing))
            }
        }
    }

    private fun showBrushChooserDialog(){
        val brushDialog = Dialog(this)
        brushDialog.setContentView(R.layout.dialog_brush_size)

        val smallBtn: ImageButton = brushDialog.findViewById(R.id.smallBrushBtn)
        smallBtn.setOnClickListener {
            drawingView?.setBrushSize(10.toFloat())
            smallBtn.setImageResource(R.drawable.selected_brush_size)
            brushDialog.dismiss()
        }
        val mediumBtn: ImageButton = brushDialog.findViewById(R.id.mediumBrushBtn)
        mediumBtn.setOnClickListener {
            drawingView?.setBrushSize(20.toFloat())
            brushDialog.dismiss()
        }
        val largeBtn: ImageButton = brushDialog.findViewById(R.id.largeBrushBtn)
        largeBtn.setOnClickListener {
            drawingView?.setBrushSize(30.toFloat())
            brushDialog.dismiss()
        }
        brushDialog.show()
        if(drawingView?.getBrushSize() == TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10.toFloat(), resources.displayMetrics)){
            smallBtn.setBackgroundResource(R.drawable.selected_brush_size)
        }
        else if(drawingView?.getBrushSize() == TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 20.toFloat(), resources.displayMetrics)){
            mediumBtn.setBackgroundResource(R.drawable.selected_brush_size)
        }
        else if(drawingView?.getBrushSize() == TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 30.toFloat(), resources.displayMetrics)){
            largeBtn.setBackgroundResource(R.drawable.selected_brush_size)
        }
    }

    @SuppressLint("SetTextI18n")
    fun colorPaint(view: View) {

        val imageButton = view as ImageButton
        val colorTag = imageButton.tag.toString()
        val id = imageButton.id

        if (id != -1) {
            val pickerDialog = Dialog(this)
            pickerDialog.setContentView(R.layout.dialog_color_picker)
            val colorPickerView: ColorPickerView = pickerDialog.findViewById(R.id.colorPickerView)
            val alphaBar: AlphaSlideBar = pickerDialog.findViewById(R.id.alphaBar)
            val brightBar: BrightnessSlideBar = pickerDialog.findViewById(R.id.brightBar)
            val colorLayout: LinearLayout = pickerDialog.findViewById(R.id.colorLayout)
            val colorCode: TextView = pickerDialog.findViewById(R.id.colorCode)
            val selectColorBtn: Button = pickerDialog.findViewById(R.id.selectColorBtn)
            val searchColorBtn: Button = pickerDialog.findViewById(R.id.searchColorBtn)

            colorPickerView.preferenceName = "MyColorPicker";

            colorPickerView.attachBrightnessSlider(brightBar)
            colorPickerView.attachAlphaSlider(alphaBar)

            colorPickerView.setColorListener(ColorEnvelopeListener { envelope, _ ->
                colorCode.text = "#" + envelope.hexCode
                colorLayout.setBackgroundColor(envelope.color)

                selectColorBtn.setOnClickListener {
                    pickerDialog.dismiss()
                    drawingView?.setColor(colorCode.text as String)

                    imageButton.setBackgroundColor(envelope.color)
                    randomBtn?.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.color_pressed))

                    if (view != colorBtn) {
                        colorBtn?.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.color_state))

                        colorBtn = view
                    }
                    ColorPickerPreferenceManager.getInstance(this).saveColorPickerData(colorPickerView);
                }
                searchColorBtn.setOnClickListener {
                    pickerDialog.dismiss()
                    val searchDialog = Dialog(this)
                    searchDialog.setContentView(R.layout.dialog_search_color)

                    val searchEdit: EditText = searchDialog.findViewById(R.id.searchEdit)
                    val searchBtn: Button = searchDialog.findViewById(R.id.searchBtn)
                    val colorSearchLayout: LinearLayout = searchDialog.findViewById(R.id.colorSearchLayout)

                    if (stringColor != ""){
                        searchEdit.setText(stringColor)
                        colorSearchLayout.setBackgroundColor(Color.parseColor("#" + searchEdit.text))
                        searchBtn.text = "SELECT"
                    }

                    searchEdit.addTextChangedListener(object : TextWatcher {
                        override fun afterTextChanged(s: Editable?) {
                        }
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                        }
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                            if (searchEdit.length() < 6) {
                                searchBtn.text = "SEARCH"
                            }
                            else if (searchEdit.length() == 6) {
                                colorSearchLayout.setBackgroundColor(Color.parseColor("#" + searchEdit.text))
                                searchBtn.text = "SELECT"
                            }
                        }
                    })

                    searchBtn.setOnClickListener {
                        if (searchEdit.length() < 6) {
                            Toast.makeText(this@MainActivity, "Please enter 6 digits", Toast.LENGTH_LONG).show()
                        }
                        else{
                            if (searchBtn.text == "SEARCH") {
                                colorSearchLayout.setBackgroundColor(Color.parseColor("#" + searchEdit.text))
                                searchBtn.text = "SELECT"
                            }
                            else if (searchBtn.text == "SELECT") {
                                searchDialog.dismiss()
                                stringColor = searchEdit.text.toString()
                                drawingView?.setColor("#$stringColor")

                                imageButton.setBackgroundColor(Color.parseColor("#$stringColor"))
                                randomBtn?.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.color_pressed))
                            }
                        }
                    }
                    searchDialog.show()
                }
            })
            pickerDialog.show()
        } else {
            if (view != colorBtn) {
                drawingView?.setColor(colorTag)
                imageButton.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.color_pressed))

                colorBtn?.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.color_state))
                randomBtn?.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.ic_add))

                colorBtn = view
            }


        }
    }

    fun undoPath(view: View){
        drawingView?.onClickUndo()
    }

    fun redoPath(view: View){
        drawingView?.onClickRedo()
    }

    private fun uploadImage(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R){
            if (Environment.isExternalStorageManager()){
                val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
                openGalleryLauncher.launch(pickIntent)
            }
            else{
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                val uri = Uri.fromParts("package", packageName, null)
                intent.data = uri
                startActivity(intent)
                Toast.makeText(this, "Please grant permission to access external storage.", Toast.LENGTH_LONG).show()
            }
        }
        else{
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.READ_EXTERNAL_STORAGE)){
                showRationaleDialog("Simple Drawing App", "Simple Drawing App needs to Access your External Storage")
            }
            else{
                requestPermission.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
            }
        }
    }

    private fun showRationaleDialog(title: String, message: String){
        val builder: AlertDialog.Builder = AlertDialog.Builder(this)
        builder.setTitle(title)
            .setMessage(message)
            .setPositiveButton("Cancel"){
                dialog, _ ->
                dialog.dismiss()
            }
        builder.create().show()
    }

    private fun getBitmapFromView (view: View): Bitmap{
        val returnedBitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(returnedBitmap)
        val bgDrawable = view.background
        if (bgDrawable != null){
            bgDrawable.draw(canvas)
        }
        else{
            canvas.drawColor(Color.WHITE)
        }
        view.draw(canvas)
        return returnedBitmap
    }

    private suspend fun saveBitmap(view: View) {
        withContext(Dispatchers.IO) {
            val bitmap = getBitmapFromView(view)
            val resolver = applicationContext.contentResolver
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "SimpleDrawingApp_${System.currentTimeMillis() / 1000}.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(MediaStore.Images.Media.DATE_ADDED, System.currentTimeMillis() / 1000)
                put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            }
            var imageUri: Uri? = null
            resolver.run {
                imageUri = insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                imageUri?.let {
                    openOutputStream(it)?.use { outputStream ->
                        bitmap.compress(Bitmap.CompressFormat.PNG, 90, outputStream)
                    }
                }
            }
            runOnUiThread {
                cancelProgressDialog()
                if (imageUri != null) {
                    Toast.makeText(this@MainActivity, "File saved successfully: $imageUri", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this@MainActivity, "Something went wrong while saving the file...", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private suspend fun shareBitmap(bitmap: Bitmap?): String{
        var result = ""
        withContext(Dispatchers.IO){
            if (bitmap != null){
                try {
                    val bytes = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.PNG, 90, bytes)

                    val file = File(externalCacheDir?.absoluteFile.toString() + File.separator + "SimpleDrawingApp_${System.currentTimeMillis()/1000}.png")

                    result = file.absolutePath

                    shareImage(result)

                }
                catch (e: Exception){
                    result = ""
                    e.printStackTrace()
                }
            }
        }

        return  result
    }

    private fun showProgressDialog(){
        customProgressDialog = Dialog(this)
        customProgressDialog?.setContentView(R.layout.dialog_custom_progress)
        customProgressDialog?.show()
    }

    private fun cancelProgressDialog(){
        if(customProgressDialog!=null){
            customProgressDialog?.dismiss()
            customProgressDialog = null
        }
    }

    private fun shareImage(result:String){
        MediaScannerConnection.scanFile(this, arrayOf(result), null){
                path, uri ->
            val shareIntent = Intent()
            shareIntent.action = Intent.ACTION_SEND
            shareIntent.putExtra(Intent.EXTRA_STREAM, uri)
            shareIntent.type = "image/png"
            startActivity(Intent.createChooser(shareIntent, "Share"))
        }
    }

}