//package com.lodz.android.hermes.paho.android.service
//
//import android.os.Parcel
//import android.os.Parcelable
//import org.eclipse.paho.client.mqttv3.MqttMessage
//
///**
// * <p>
// * A way to flow MqttMessages via Bundles/Intents
// * </p>
// *
// * <p>
// * An application will probably use this only when receiving a message from a
// * Service in a Bundle - the necessary code will be something like this :-
// * </p>
// * <pre>
// * <code>
// * 	private void messageArrivedAction(Bundle data) {
// * 		ParcelableMqttMessage message = (ParcelableMqttMessage) data
// * 			.getParcelable(MqttServiceConstants.CALLBACK_MESSAGE_PARCEL);
// *		<i>Use the normal {@link MqttMessage} methods on the the message object.</i>
// * 	}
// *
// * </code>
// * </pre>
// *
// * <p>
// * It is unlikely that an application will directly use the methods which are
// * specific to this class.
// * </p>
// */
//class ParcelableMqttMessage : MqttMessage, Parcelable {
//
//    private var messageId: String = ""
//
//    constructor(original: MqttMessage) : super(original.payload) {
//        qos = original.qos
//        isRetained = original.isRetained
//        isDuplicate = original.isDuplicate
//    }
//
//    constructor(parcel: Parcel) : super(parcel.createByteArray()) {
//        qos = parcel.readInt()
//        val flags = parcel.createBooleanArray()
//        isRetained = flags?.get(0) ?: false
//        isDuplicate = flags?.get(1) ?: false
//        messageId = parcel.readString() ?: ""
//    }
//
//    override fun writeToParcel(parcel: Parcel, flags: Int) {
//        parcel.writeByteArray(payload)
//        parcel.writeInt(qos)
//        parcel.writeBooleanArray(booleanArrayOf(isRetained, isDuplicate))
//        parcel.writeString(messageId)
//    }
//
//    override fun describeContents(): Int {
//        return 0
//    }
//
//    companion object CREATOR : Parcelable.Creator<ParcelableMqttMessage> {
//        override fun createFromParcel(parcel: Parcel): ParcelableMqttMessage {
//            return ParcelableMqttMessage(parcel)
//        }
//
//        override fun newArray(size: Int): Array<ParcelableMqttMessage?> {
//            return arrayOfNulls(size)
//        }
//    }
//}