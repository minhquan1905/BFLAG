package com.example.minhquan.bflagclient.chat.roomchat

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.design.widget.Snackbar
import android.support.v4.app.Fragment
import android.support.v7.widget.LinearLayoutManager
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.minhquan.bflagclient.R
import com.example.minhquan.bflagclient.adapter.ChatAdapter
import com.example.minhquan.bflagclient.base.BaseResponse
import com.example.minhquan.bflagclient.model.*
import com.example.minhquan.bflagclient.utils.*
import com.hosopy.actioncable.Subscription
import com.tbruyelle.rxpermissions2.RxPermissions
import io.reactivex.disposables.Disposable
import kotlinx.android.synthetic.main.fragment_chat_room.*
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

const val ACTION_TYPE = "send_data"
const val CAMERA_REQUEST_CODE = 100
const val GALLERY_REQUEST_CODE = 200
const val IMAGE_DIRECTORY_PATH = "/Bflag"
const val IMAGE = "image"

class ChatRoomFragment : Fragment(), ChatRoomContract.View {

    private lateinit var presenter: ChatRoomContract.Presenter
    private lateinit var chatAdapter: ChatAdapter
    private lateinit var token: String
    private lateinit var user: User
    private lateinit var chat: Chat
    private lateinit var subscription : Subscription


    private var historyChat: MutableList<Chat> = mutableListOf()
    private var localChat: MutableList<Chat> = mutableListOf()
    private var disposable: Disposable? = null
    private var offset = 0
    private var smoothScroll: Int = 10
    private var connected = false

    private var room = 1
    private val sdf : SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private var localImage: MutableList<String> = mutableListOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        return inflater.inflate(R.layout.fragment_chat_room, container, false) as ViewGroup
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ChatPresenter(this)

        //Get current user data
        token =  SharedPreferenceHelper.getInstance(context!!).getToken()!!
        user =  SharedPreferenceHelper.getInstance(context!!).getUser()!!

        setupView()

        //edt_chat.setText(room)

    }

    fun setRoom(room: Int) {
        this.room = room
    }

    private fun setupView() {

        chatAdapter = ChatAdapter(activity!!)
        rv_chat.layoutManager = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
        rv_chat.adapter = chatAdapter

        val rxPermissions = RxPermissions(this)
        rxPermissions.setLogging(true)


        // Set up listener for button send message
        img_chat_sender.setOnClickListener {

            if(!edt_chat.text.isEmpty()) {

                sendChat(edt_chat.text.toString(), null, null)

            }
        }

        // Set up listener for button open camera to take a photo
        img_chat_camera.setOnClickListener {
            disposable = rxPermissions
                    .request(Manifest.permission.CAMERA,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    .subscribe { granted ->
                        if (granted)
                            takePhotoFromCamera()
                        else
                            Toast.makeText(context!!,
                                    "Permission denied, can't open Camera!",
                                    Toast.LENGTH_SHORT).show()
                    }
        }

        // Set up listener for button open photo gallery of device
        img_chat_gallery.setOnClickListener {
            disposable = rxPermissions
                    .request(Manifest.permission.READ_EXTERNAL_STORAGE)
                    .subscribe { granted ->
                        if (granted)
                            choosePhotoFromGallery()
                        else
                            Toast.makeText(context!!,
                                    "Permission denied, can't open photo Gallery!",
                                    Toast.LENGTH_SHORT).show()
                    }
        }

        // Set up for connecting to server
        ChatServerUtil.startConnectWebSocket(this, token, room, CHAT)

    }

    /**
     * Function for take photo from camera by making an ACTION_IMAGE_CAPTURE Intent
     */
    private fun takePhotoFromCamera() {

        val cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        startActivityForResult(cameraIntent, CAMERA_REQUEST_CODE)

    }

    /**
     * Function for choose photo from gallery by making an ACTION_PICK Intent
     */
    private fun choosePhotoFromGallery() {

        val galleryIntent = Intent(Intent.ACTION_PICK,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivityForResult(galleryIntent, GALLERY_REQUEST_CODE)

    }

    /**
     * Function for handle data that return from sent Intent
     * @param requestCode param to determine action type: camera or gallery
     *
     */
    override fun onActivityResult(requestCode:Int, resultCode:Int, data: Intent?) {

        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_CANCELED)
            return

        when (requestCode) {
            CAMERA_REQUEST_CODE -> {

                val thumbnail = data!!.extras!!.get("data") as Bitmap
                val path = context!!.savePhoto(thumbnail)

                Toast.makeText(context!!, "Image Saved!", Toast.LENGTH_SHORT).show()

                sendChat(null, path, Uri.fromFile(File(path)))
            }
            GALLERY_REQUEST_CODE -> {

                val contentURI = data?.data
                try {

                    val path = context!!.getPath(contentURI!!)
                    localImage.add(path)

                    sendChat(null, path, contentURI)

                }
                catch (e: Throwable) {
                    e.printStackTrace()
                    Toast.makeText(context!!, "Failed!", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun sendChat(text: String?, image: String?, uri: Uri?) {

        chat = Chat(Friend(user.email, user.username, user.profileImage), Message(text, image),
                sdf.format(Date()))

        if (text != null) edt_chat.text = null

        smoothScroll = chatAdapter.setData(chat)
        rv_chat.smoothScrollToPosition(smoothScroll)
        localChat.add(chat)

        if (isNetworkConnected()) {
            Log.d("TAG", "Ready to send to server")
            if (text != null && connected)
                presenter.startSendLogChat(ACTION_TYPE, localChat, subscription)
            else {
                val filePart = prepareFilePart(IMAGE, uri!!, context!!)
                presenter.startSendImageChat(token, filePart, room)

            }
        }
    }

    override fun onConnectWebSocketSuccess(subscription: Subscription) {

        this.subscription = subscription
        connected = true
        presenter.startGetHistoryChat(token, room, offset)
    }

    override fun onGetHistoryChatSuccess(result: HistoryChatResponse) {

        for (i in 0 until result.listChats!!.size) {
            smoothScroll = chatAdapter.setData(result.listChats[i])
        }

        rv_chat.smoothScrollToPosition(smoothScroll)
    }

    override fun onReceiveLogChatSuccess(message: BaseResponse) {
        activity!!.runOnUiThread {

            chat = message as Chat

            if (chat.friend!!.email != SharedPreferenceHelper.getInstance(context!!).getUser()!!.email) {

                smoothScroll = chatAdapter.setData(chat)
                rv_chat.smoothScrollToPosition(smoothScroll)
            }
            historyChat.add(chat)
        }
    }

    override fun onSendImageChatSuccess(result: SuccessResponse) {
        Log.d("Send image", "Success")
    }

    override fun showProgress(isShow: Boolean) {
        activity!!.runOnUiThread {
            loader.visibility = if (isShow) View.VISIBLE else View.GONE
        }
    }

    override fun showError(message: String) {
        Log.e("Error return", message)

        Snackbar.make(activity!!.findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG)
                .show()

    }

    override fun setPresenter(presenter: ChatRoomContract.Presenter) {
        this.presenter = presenter
    }

    override fun onUnknownError(error: String) {
        showError(error)
    }

    override fun onTimeout() {
        showError(TIME_OUT)
    }

    override fun onNetworkError() {
        showError(NETWORK_ERROR)
    }

    override fun isNetworkConnected(): Boolean {
        return ConnectivityUtil.isConnected(this.activity!!)
    }


}