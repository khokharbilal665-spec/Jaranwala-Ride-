package com.example.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class PakistanLocationPoint(
    val name: String,
    val urduName: String,
    val category: String,
    val latitude: Double,
    val longitude: Double,
    val description: String = ""
)

object PakistanGeoService {
    // Official coordinates for Jaranwala, Punjab, Pakistan
    const val JARANWALA_LAT = 31.3333
    const val JARANWALA_LNG = 73.4333

    // Approximate Pakistan bounding box for validation
    const val PAKISTAN_MIN_LAT = 23.5
    const val PAKISTAN_MAX_LAT = 37.1
    const val PAKISTAN_MIN_LNG = 60.5
    const val PAKISTAN_MAX_LNG = 77.8

    fun isWithinPakistan(lat: Double, lng: Double): Boolean {
        return lat in PAKISTAN_MIN_LAT..PAKISTAN_MAX_LAT && lng in PAKISTAN_MIN_LNG..PAKISTAN_MAX_LNG
    }

    // Comprehensive catalog of locations in Jaranwala, nearby villages/tehsils, and across Pakistan
    val PAKISTAN_LOCATIONS: List<PakistanLocationPoint> = listOf(
        // Jaranwala Core & Landmarks
        PakistanLocationPoint("Committee Chowk, Jaranwala", "کمیٹی چوک، جرانوالہ", "Jaranwala Hub", 31.3342, 73.4194, "Central City Landmark & Business Center"),
        PakistanLocationPoint("Jaranwala Railway Station", "جرانوالہ ریلوے اسٹیشن", "Jaranwala Hub", 31.3381, 73.4278, "Main Railway Transit Hub"),
        PakistanLocationPoint("Cinema Chowk, Jaranwala", "سینما چوک، جرانوالہ", "Jaranwala Hub", 31.3325, 73.4210, "Historic commercial junction"),
        PakistanLocationPoint("Katchery Bazar, Jaranwala", "کچہری بازار، جرانوالہ", "Jaranwala Hub", 31.3350, 73.4225, "Bustling retail and market street"),
        PakistanLocationPoint("Ghalla Mandi (Grain Market), Jaranwala", "غلہ منڈی، جرانوالہ", "Jaranwala Hub", 31.3365, 73.4260, "Asia's renowned agricultural grain market"),
        PakistanLocationPoint("Mandi Road, Jaranwala", "منڈی روڈ، جرانوالہ", "Jaranwala Hub", 31.3370, 73.4240, "Key transit corridor"),
        PakistanLocationPoint("DHQ Hospital Jaranwala", "ڈی ایچ کیو ہسپتال، جرانوالہ", "Jaranwala Hub", 31.3315, 73.4160, "District Headquarter Healthcare Facility"),
        PakistanLocationPoint("Government Degree College, Jaranwala", "گورنمنٹ ڈگری کالج، جرانوالہ", "Jaranwala Hub", 31.3410, 73.4310, "Educational Institute"),
        PakistanLocationPoint("Royal Palm Marriage Hall, Jaranwala", "رائل پام میرج ہال، جرانوالہ", "Jaranwala Hub", 31.3450, 73.4380, "Wedding & Event Venue"),
        PakistanLocationPoint("Taj Palace Marquee, Jaranwala", "تاج پیلس مارکی، جرانوالہ", "Jaranwala Hub", 31.3480, 73.4420, "Wedding & Banquet Facility"),
        PakistanLocationPoint("Sub-Divisional Courts Complex", "سب ڈویژنل کورٹس، جرانوالہ", "Jaranwala Hub", 31.3355, 73.4230, "Judicial & Administration Center"),
        PakistanLocationPoint("Rescue 1122 Station, Jaranwala", "ریسکیو 1122 اسٹیشن، جرانوالہ", "Jaranwala Hub", 31.3305, 73.4180, "Emergency Services Headquarters"),
        PakistanLocationPoint("Jaranwala Sports Stadium", "جرانوالہ اسپورٹس اسٹیڈیم", "Jaranwala Hub", 31.3280, 73.4130, "City Playground & Sports Complex"),
        PakistanLocationPoint("Model City, Jaranwala", "ماڈل سٹی، جرانوالہ", "Jaranwala Hub", 31.3430, 73.4290, "Residential Area"),
        PakistanLocationPoint("Canal Rest House, Jaranwala", "کینال ریسٹ ہاؤس، جرانوالہ", "Jaranwala Hub", 31.3390, 73.4215, "Historic Gugera Canal Rest House"),
        PakistanLocationPoint("Faisalabad Road Toll Plaza, Jaranwala", "فیصل آباد روڈ ٹول پلازہ", "Jaranwala Hub", 31.3490, 73.3980, "Highway Entrance to Faisalabad"),
        PakistanLocationPoint("Lahore Road By-Pass, Jaranwala", "لاہور روڈ بائی پاس، جرانوالہ", "Jaranwala Hub", 31.3520, 73.4510, "By-pass connecting to Lahore & M-3"),
        PakistanLocationPoint("Kot Daya Kishan, Jaranwala", "کوٹ دیا کشن، جرانوالہ", "Jaranwala Hub", 31.3200, 73.4450, "Historic urban settlement"),
        PakistanLocationPoint("Mandi Waris Pura, Jaranwala", "وارث پورہ منڈی، جرانوالہ", "Jaranwala Hub", 31.3250, 73.4100, "Commercial quarter"),
        PakistanLocationPoint("Alawalpur, Jaranwala", "علاول پور، جرانوالہ", "Jaranwala Hub", 31.3600, 73.4600, "Suburban Jaranwala settlement"),

        // Jaranwala to Syedwala (Siyadwala) Corridor & Rural Settlements
        PakistanLocationPoint("Syedwala, Jaranwala", "سیدوالا (سیادوالا)، جرانوالہ", "Jaranwala-Syedwala Corridor", 31.2580, 73.6120, "Historic town on Ravi River with boat ghat & bridge"),
        PakistanLocationPoint("Mandi Syedwala", "منڈی سیدوالا", "Jaranwala-Syedwala Corridor", 31.2595, 73.6150, "Trading hub and grain mandi of Syedwala"),
        PakistanLocationPoint("Ravi River Bridge Syedwala", "دریائے راوی پل سیدوالا", "Jaranwala-Syedwala Corridor", 31.2530, 73.6210, "Inter-district bridge connecting Jaranwala to Okara"),
        PakistanLocationPoint("Jhok Ditta, Syedwala Road", "جھوک دتا، سیدوالا روڈ", "Jaranwala-Syedwala Corridor", 31.2750, 73.5850, "Rural village along Syedwala corridor"),
        PakistanLocationPoint("Mari Pattan, Jaranwala", "ماڑی پتن، جرانوالہ", "Jaranwala-Syedwala Corridor", 31.2650, 73.5950, "Historic river bank village settlement"),
        PakistanLocationPoint("Miranpur, Jaranwala", "میران پور، جرانوالہ", "Jaranwala-Syedwala Corridor", 31.2820, 73.5600, "Agricultural community on Syedwala link road"),
        PakistanLocationPoint("Wasavewala, Jaranwala", "وساوے والا، جرانوالہ", "Jaranwala-Syedwala Corridor", 31.2910, 73.5380, "Rural settlement on Jaranwala-Syedwala route"),
        PakistanLocationPoint("Head Gugera, Jaranwala", "ہیڈ گوگیرہ، جرانوالہ", "Jaranwala-Syedwala Corridor", 31.3050, 73.5120, "Gugera Branch Canal Headworks and bridge"),
        PakistanLocationPoint("Dahr Village, Jaranwala", "ڈاہر، جرانوالہ", "Jaranwala-Syedwala Corridor", 31.2900, 73.5500, "Farming village along canal feeder"),

        // Exhaustive Chaks (Gugera Branch Villages) in Jaranwala Tehsil
        PakistanLocationPoint("Chak 56 GB, Jaranwala", "چک 56 جی بی، جرانوالہ", "Jaranwala Village", 31.2800, 73.4420, "Rural farming community"),
        PakistanLocationPoint("Chak 57 GB, Jaranwala", "چک 57 جی بی، جرانوالہ", "Jaranwala Village", 31.2890, 73.4510, "Canal irrigated village"),
        PakistanLocationPoint("Chak 58 GB, Jaranwala", "چک 58 جی بی، جرانوالہ", "Jaranwala Village", 31.2980, 73.4610, "Agricultural village community"),
        PakistanLocationPoint("Chak 59 GB, Jaranwala", "چک 59 جی بی، جرانوالہ", "Jaranwala Village", 31.3080, 73.4720, "Village settlement"),
        PakistanLocationPoint("Chak 60 GB, Jaranwala", "چک 60 جی بی، جرانوالہ", "Jaranwala Village", 31.3180, 73.4830, "Village community near Jaranwala town"),
        PakistanLocationPoint("Chak 61 GB, Jaranwala", "چک 61 جی بی، جرانوالہ", "Jaranwala Village", 31.3290, 73.4920, "East Jaranwala agricultural chak"),
        PakistanLocationPoint("Chak 63 GB, Jaranwala", "چک 63 جی بی، جرانوالہ", "Jaranwala Village", 31.3480, 73.4350, "North-East village settlement"),
        PakistanLocationPoint("Chak 64 GB, Jaranwala", "چک 64 جی بی، جرانوالہ", "Jaranwala Village", 31.3650, 73.4150, "Canal-side village"),
        PakistanLocationPoint("Chak 65 GB, Jaranwala", "چک 65 جی بی، جرانوالہ", "Jaranwala Village", 31.3780, 73.3980, "Village community near canal"),
        PakistanLocationPoint("Chak 66 GB, Jaranwala", "چک 66 جی بی، جرانوالہ", "Jaranwala Village", 31.3920, 73.3820, "Agricultural chak"),
        PakistanLocationPoint("Chak 67 GB, Jaranwala", "چک 67 جی بی، جرانوالہ", "Jaranwala Village", 31.4050, 73.3650, "Village bordering Awagat"),
        PakistanLocationPoint("Chak 98 GB, Jaranwala", "چک 98 جی بی، جرانوالہ", "Jaranwala Village", 31.4350, 73.4150, "Northern Jaranwala tehsil settlement"),
        PakistanLocationPoint("Chak 103 GB, Jaranwala", "چک 103 جی بی، جرانوالہ", "Jaranwala Village", 31.4550, 73.4350, "Farming settlement"),
        PakistanLocationPoint("Chak 107 GB, Jaranwala", "چک 107 جی بی، جرانوالہ", "Jaranwala Village", 31.4720, 73.4480, "Rural village near Lundianwala"),
        PakistanLocationPoint("Chak 109 GB, Jaranwala", "چک 109 جی بی، جرانوالہ", "Jaranwala Village", 31.4900, 73.4650, "Northern tehsil border chak"),
        PakistanLocationPoint("Chak 115 GB, Jaranwala", "چک 115 جی بی، جرانوالہ", "Jaranwala Village", 31.3540, 73.4720, "Village on Lahore Road"),
        PakistanLocationPoint("Chak 116 GB, Jaranwala", "چک 116 جی بی، جرانوالہ", "Jaranwala Village", 31.3620, 73.4890, "Agricultural community"),
        PakistanLocationPoint("Chak 117 GB, Jaranwala", "چک 117 جی بی، جرانوالہ", "Jaranwala Village", 31.3710, 73.5080, "Rural village"),
        PakistanLocationPoint("Chak 118 GB, Jaranwala", "چک 118 جی بی، جرانوالہ", "Jaranwala Village", 31.3800, 73.5250, "Eastern Jaranwala settlement"),
        PakistanLocationPoint("Chak 126 GB, Jaranwala", "چک 126 جی بی، جرانوالہ", "Jaranwala Village", 31.3210, 73.4910, "Farming community"),
        PakistanLocationPoint("Chak 127 GB, Jaranwala", "چک 127 جی بی، جرانوالہ", "Jaranwala Village", 31.3100, 73.5050, "Rural chak"),
        PakistanLocationPoint("Chak 128 GB, Jaranwala", "چک 128 جی بی، جرانوالہ", "Jaranwala Village", 31.2980, 73.5200, "Agricultural settlement"),
        PakistanLocationPoint("Chak 129 GB, Jaranwala", "چک 129 جی بی، جرانوالہ", "Jaranwala Village", 31.2860, 73.5400, "Canal irrigated village"),
        PakistanLocationPoint("Chak 234 GB, Jaranwala", "چک 234 جی بی، جرانوالہ", "Jaranwala Village", 31.3150, 73.4850, "Rural chak along link road"),
        PakistanLocationPoint("Chak 235 GB, Jaranwala", "چک 235 جی بی، جرانوالہ", "Jaranwala Village", 31.3190, 73.4980, "Village community"),
        PakistanLocationPoint("Chak 236 GB, Jaranwala", "چک 236 جی بی، جرانوالہ", "Jaranwala Village", 31.3230, 73.5150, "Agricultural settlement"),
        PakistanLocationPoint("Chak 238 GB, Jaranwala", "چک 238 جی بی، جرانوالہ", "Jaranwala Village", 31.3280, 73.5350, "Farming village"),
        PakistanLocationPoint("Chak 239 GB, Jaranwala", "چک 239 جی بی، جرانوالہ", "Jaranwala Village", 31.3080, 73.4550, "Rural community"),
        PakistanLocationPoint("Chak 240 GB, Jaranwala", "چک 240 جی بی، جرانوالہ", "Jaranwala Village", 31.3120, 73.4010, "Rural village community"),
        PakistanLocationPoint("Chak 242 GB, Jaranwala", "چک 242 جی بی، جرانوالہ", "Jaranwala Village", 31.3320, 73.3850, "Western Jaranwala village"),
        PakistanLocationPoint("Chak 244 GB, Jaranwala", "چک 244 جی بی، جرانوالہ", "Jaranwala Village", 31.3450, 73.3650, "Faisalabad border village"),

        // Towns & Markets in Jaranwala Sub-Division
        PakistanLocationPoint("Buchiana, Jaranwala", "بچیانہ، جرانوالہ", "Jaranwala Town", 31.4285, 73.5780, "Railway junction and grain market"),
        PakistanLocationPoint("Bucheke, Punjab", "بچیکی، پنجاب", "Nearby Town", 31.4420, 73.6950, "Town on Lahore-Faisalabad highway"),
        PakistanLocationPoint("Lundianwala, Jaranwala", "لنڈیانوالہ، جرانوالہ", "Jaranwala Town", 31.4870, 73.4210, "Town and police station area"),
        PakistanLocationPoint("Awagat, Jaranwala", "آواگت، جرانوالہ", "Jaranwala Town", 31.4110, 73.3420, "Agricultural center on Jaranwala-FSD road"),
        PakistanLocationPoint("Rodala Road, Jaranwala", "روڈالہ روڈ، جرانوالہ", "Jaranwala Town", 31.3850, 73.5120, "Rural township and railway stop"),
        PakistanLocationPoint("Satyana, Jaranwala", "ستیانہ، جرانوالہ", "Jaranwala Town", 31.2850, 73.2840, "Major agricultural town and market"),
        PakistanLocationPoint("Khurrianwala", "کھرڑیانوالہ", "Nearby City", 31.5015, 73.2710, "Industrial textile hub connecting Jaranwala & M-3"),
        PakistanLocationPoint("Shahkot", "شاہکوٹ", "Nearby City", 31.5714, 73.4842, "Historic town at Baba Nau Gaz Pir hills"),
        PakistanLocationPoint("Sangla Hill", "سانگلہ ہل", "Nearby City", 31.7167, 73.3833, "Historic town in Nankana Sahib District"),
        PakistanLocationPoint("Nankana Sahib", "ننکانہ صاحب", "Nearby City", 31.4504, 73.7065, "Historic international pilgrimage city"),
        PakistanLocationPoint("Warburton", "واربرٹن", "Nearby Town", 31.5470, 73.8320, "Township near Nankana"),
        PakistanLocationPoint("Tandlianwala", "تاندلیانوالہ", "Nearby City", 31.0336, 73.1315, "Tehsil headquarters in Faisalabad District"),
        PakistanLocationPoint("Samundri", "سمندری", "Nearby City", 31.0638, 72.9614, "Major agricultural tehsil of Faisalabad"),

        // Major Punjab Cities & District Headquarters
        PakistanLocationPoint("Faisalabad (Lyallpur)", "فیصل آباد", "Punjab Hub", 31.4187, 73.0791, "Textile capital of Pakistan, 35 km from Jaranwala"),
        PakistanLocationPoint("Lahore", "لاہور", "Punjab Hub", 31.5204, 74.3587, "Provincial Capital of Punjab, Heart of Pakistan"),
        PakistanLocationPoint("Sheikhupura", "شیخوپورہ", "Punjab Hub", 31.7167, 73.9850, "Historic industrial city near Lahore"),
        PakistanLocationPoint("Kasur", "قصور", "Punjab Hub", 31.1179, 74.4461, "City of Baba Bulleh Shah"),
        PakistanLocationPoint("Okara", "اوکاڑہ", "Punjab Hub", 30.8138, 73.4534, "City of Dairy & Agriculture, near Syedwala"),
        PakistanLocationPoint("Sahiwal", "ساہیوال", "Punjab Hub", 30.6777, 73.1068, "Harappa Civilisation District"),
        PakistanLocationPoint("Chiniot", "چنیوٹ", "Punjab Hub", 31.7200, 72.9789, "Historic city of craft on Chenab"),
        PakistanLocationPoint("Gujranwala", "گوجرانوالہ", "Punjab Hub", 32.1877, 74.1945, "City of Wrestlers & Industry"),
        PakistanLocationPoint("Sialkot", "سیالکوٹ", "Punjab Hub", 32.4945, 74.5229, "Export hub of Sports & Surgical equipment"),
        PakistanLocationPoint("Sargodha", "سرگودھا", "Punjab Hub", 32.0836, 72.6711, "City of Eagles & Citrus"),
        PakistanLocationPoint("Multan", "ملتان", "Punjab Hub", 30.1575, 71.5249, "City of Saints & Mangos"),
        PakistanLocationPoint("Bahawalpur", "بہاولپور", "Punjab Hub", 29.3544, 71.6911, "Historic Princely State & Palaces"),
        PakistanLocationPoint("Rahim Yar Khan", "رحیم یار خان", "Punjab Hub", 28.4212, 70.2989, "Southern Punjab Commercial Hub"),
        PakistanLocationPoint("Dera Ghazi Khan", "ڈیرہ غازی خان", "Punjab Hub", 30.0561, 70.6348, "Suleman Range gateway"),
        PakistanLocationPoint("Jhang", "جھنگ", "Punjab Hub", 31.2781, 72.3317, "Historic city of Heer Ranjha on Chenab"),
        PakistanLocationPoint("Toba Tek Singh", "ٹوبہ ٹیک سنگھ", "Punjab Hub", 30.9709, 72.4826, "Central Punjab district headquarters"),
        PakistanLocationPoint("Gujrat", "گجرات", "Punjab Hub", 32.5742, 74.0754, "Industrial fan and furniture city"),
        PakistanLocationPoint("Jhelum", "جہلم", "Punjab Hub", 32.9425, 73.7257, "Land of Soldiers & Salt Range"),
        PakistanLocationPoint("Attock", "اٹک", "Punjab Hub", 33.7667, 72.3667, "Indus River confluence fortress city"),
        PakistanLocationPoint("Mianwali", "میانوالی", "Punjab Hub", 32.5853, 71.5436, "Chashma Barrage and Salt Range"),
        PakistanLocationPoint("Bhakkar", "بھکر", "Punjab Hub", 31.6253, 71.0657, "Thal desert district headquarters"),
        PakistanLocationPoint("Layyah", "لیہ", "Punjab Hub", 30.9613, 70.9390, "Thal and Indus agricultural city"),
        PakistanLocationPoint("Muzaffargarh", "مظفر گڑھ", "Punjab Hub", 30.0744, 71.1925, "Between Chenab and Indus"),
        PakistanLocationPoint("Vehari", "وہاڑی", "Punjab Hub", 30.0419, 72.3489, "Cotton and agricultural hub"),
        PakistanLocationPoint("Khanewal", "خانیوال", "Punjab Hub", 30.3017, 71.9321, "Major railway junction of Punjab"),
        PakistanLocationPoint("Pakpattan", "پاکپتن", "Punjab Hub", 30.3410, 73.3866, "Shrine of Baba Farid Ganj Shakar"),
        PakistanLocationPoint("Lodhran", "لودھراں", "Punjab Hub", 29.5405, 71.6336, "Sutlej River district"),
        PakistanLocationPoint("Rajanpur", "راجن پور", "Punjab Hub", 29.1035, 70.3250, "Indus plain southern district"),
        PakistanLocationPoint("Hafizabad", "حافظ آباد", "Punjab Hub", 32.0679, 73.6857, "Famous Rice City of Pakistan"),
        PakistanLocationPoint("Mandi Bahauddin", "منڈی بہاؤالدین", "Punjab Hub", 32.5870, 73.4912, "Grain and dairy center"),
        PakistanLocationPoint("Narowal", "نارووال", "Punjab Hub", 32.0995, 74.8746, "Kartarpur corridor district"),
        PakistanLocationPoint("Chakwal", "چکوال", "Punjab Hub", 32.9328, 72.8631, "Potohar and Kallar Kahar hub"),
        PakistanLocationPoint("Khushab (Jauharabad)", "خوشاب", "Punjab Hub", 32.2967, 72.3525, "Soon Valley and Thal district"),

        // Federal Capital & Other Provinces of Pakistan
        PakistanLocationPoint("Islamabad", "اسلام آباد", "Federal Capital", 33.6844, 73.0479, "Federal Capital of Pakistan"),
        PakistanLocationPoint("Rawalpindi", "راولپنڈی", "Major City", 33.5651, 73.0169, "Twin City & Historic Garrison"),
        PakistanLocationPoint("Karachi", "کراچی", "Sindh Hub", 24.8607, 67.0011, "City of Lights, Financial Capital on Arabian Sea"),
        PakistanLocationPoint("Hyderabad", "حیدرآباد", "Sindh Hub", 25.3960, 68.3578, "Historic Sindh Metropolis on Indus"),
        PakistanLocationPoint("Sukkur", "سکھر", "Sindh Hub", 27.7052, 68.8574, "Famous Sukkur Barrage on Indus"),
        PakistanLocationPoint("Larkana", "لاڑکانہ", "Sindh Hub", 27.5590, 68.2120, "Mohenjo-daro World Heritage District"),
        PakistanLocationPoint("Mirpur Khas", "میرپور خاص", "Sindh Hub", 25.5276, 69.0159, "City of Sindhri Mangoes"),
        PakistanLocationPoint("Nawabshah (Shaheed Benazirabad)", "نواب شاہ", "Sindh Hub", 26.2483, 68.4096, "Central Sindh agricultural metropolis"),
        PakistanLocationPoint("Thatta", "ٹھٹھہ", "Sindh Hub", 24.7475, 67.9235, "Makli Necropolis historic city"),
        PakistanLocationPoint("Peshawar", "پشاور", "KP Hub", 34.0151, 71.5249, "Historic Gateway to Khyber, KP Capital"),
        PakistanLocationPoint("Mardan", "مردان", "KP Hub", 34.1989, 72.0404, "Takht-i-Bahi archaeological district"),
        PakistanLocationPoint("Abbottabad", "ایبٹ آباد", "KP Hub", 34.1688, 73.2215, "Scenic gateway to Hazara and Karakoram"),
        PakistanLocationPoint("Swat (Mingora)", "سوات (مینگورہ)", "KP Hub", 34.7758, 72.3626, "Switzerland of the East"),
        PakistanLocationPoint("Kohat", "کوہاٹ", "KP Hub", 33.5869, 71.4414, "Historic southern KP garrison and pass"),
        PakistanLocationPoint("Dera Ismail Khan", "ڈیرہ اسماعیل خان", "KP Hub", 31.8327, 70.9024, "Indus River highway hub in southern KP"),
        PakistanLocationPoint("Quetta", "کوئٹہ", "Balochistan Hub", 30.1798, 66.9750, "Provincial Capital of Balochistan, Fruit Garden"),
        PakistanLocationPoint("Gwadar", "گوادر", "Balochistan Hub", 25.1264, 62.3225, "Deep Sea Port City, Arabian Sea"),
        PakistanLocationPoint("Turbat", "تربت", "Balochistan Hub", 26.0031, 63.0544, "Kech Valley oasis and date palm capital"),
        PakistanLocationPoint("Khuzdar", "خضدار", "Balochistan Hub", 27.8119, 66.6177, "Central Balochistan crossroads city"),
        PakistanLocationPoint("Gilgit", "گلگت", "Gilgit-Baltistan Hub", 35.9208, 74.3144, "Northern Hub on Karakoram Highway"),
        PakistanLocationPoint("Skardu", "سکردو", "Gilgit-Baltistan Hub", 35.2971, 75.6333, "Gateway to K2 & Baltistan Peaks"),
        PakistanLocationPoint("Hunza (Karimabad)", "ہنزہ (کریم آباد)", "Gilgit-Baltistan Hub", 36.3167, 74.6500, "World-renowned mountain paradise"),
        PakistanLocationPoint("Muzaffarabad", "مظفرآباد", "AJK Hub", 34.3700, 73.4711, "Capital of Azad Jammu & Kashmir on Neelum"),
        PakistanLocationPoint("Mirpur AJK", "میرپور آزاد کشمیر", "AJK Hub", 33.1484, 73.7519, "City of Mangla Lake & Overseas Pakistanis"),
        PakistanLocationPoint("Rawalakot", "راولاکوٹ", "AJK Hub", 33.8584, 73.7604, "Pearl Valley of Azad Kashmir")
    )

    // Haversine distance formula in kilometers
    fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Radius of Earth in KM
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return String.format(Locale.US, "%.1f", r * c).toDouble()
    }

    // Create a custom pinned location anywhere in Pakistan (allows manual pin drops when a village is missing)
    fun createCustomPinnedLocation(lat: Double, lng: Double, customName: String? = null): PakistanLocationPoint {
        val closest = findClosestLocation(lat, lng)
        val formattedCoords = String.format(Locale.US, "%.4f, %.4f", lat, lng)
        val name = customName?.takeIf { it.isNotBlank() } ?: "Custom Pin ($formattedCoords)"
        val urdu = customName?.takeIf { it.isNotBlank() } ?: "منتخب مقام ($formattedCoords)"
        return PakistanLocationPoint(
            name = name,
            urduName = urdu,
            category = "Custom Pin / Village",
            latitude = lat,
            longitude = lng,
            description = "Manually selected pin near ${closest.name}"
        )
    }

    // Search anywhere across Pakistan (Jaranwala, Villages, Chaks, Cities)
    fun searchPakistan(query: String): List<PakistanLocationPoint> {
        val clean = query.trim().lowercase()
        if (clean.isBlank()) return PAKISTAN_LOCATIONS.take(10)

        return PAKISTAN_LOCATIONS.filter {
            it.name.lowercase().contains(clean) ||
            it.urduName.contains(clean) ||
            it.category.lowercase().contains(clean) ||
            it.description.lowercase().contains(clean)
        }.sortedBy {
            // Prioritize Jaranwala & nearby matches first
            if (it.name.lowercase().contains("jaranwala")) 0
            else if (it.category.contains("Village") || it.category.contains("Corridor")) 1
            else 2
        }
    }

    // Find closest location by coordinate
    fun findClosestLocation(lat: Double, lng: Double): PakistanLocationPoint {
        return PAKISTAN_LOCATIONS.minByOrNull {
            calculateDistanceKm(lat, lng, it.latitude, it.longitude)
        } ?: PAKISTAN_LOCATIONS[0]
    }

    // Get current GPS Location with safety check (strictly inside Pakistan)
    @SuppressLint("MissingPermission")
    fun fetchCurrentGpsLocation(
        context: Context,
        onSuccess: (PakistanLocationPoint) -> Unit,
        onError: (String) -> Unit
    ) {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!fineGranted && !coarseGranted) {
            onError("Location permission not granted. Showing Jaranwala default.")
            return
        }

        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()

            fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc: Location? ->
                    if (loc != null) {
                        val lat = loc.latitude
                        val lng = loc.longitude

                        // Enforce Pakistan boundary constraint: Never default or use locations in the US
                        if (isWithinPakistan(lat, lng)) {
                            val closest = findClosestLocation(lat, lng)
                            val customPoint = PakistanLocationPoint(
                                name = "My GPS Location (${String.format(Locale.US, "%.4f, %.4f", lat, lng)})",
                                urduName = "میری موجودہ لوکیشن",
                                category = "Current GPS",
                                latitude = lat,
                                longitude = lng,
                                description = "Near ${closest.name}"
                            )
                            onSuccess(customPoint)
                        } else {
                            // If emulator/device is set to Mountain View / US coordinates, enforce Jaranwala
                            onSuccess(PAKISTAN_LOCATIONS[0]) // Committee Chowk, Jaranwala
                        }
                    } else {
                        // Fallback to last known location
                        fusedClient.lastLocation.addOnSuccessListener { last: Location? ->
                            if (last != null && isWithinPakistan(last.latitude, last.longitude)) {
                                val closest = findClosestLocation(last.latitude, last.longitude)
                                onSuccess(
                                    PakistanLocationPoint(
                                        name = "My Location (${closest.name})",
                                        urduName = "میری لوکیشن",
                                        category = "Current GPS",
                                        latitude = last.latitude,
                                        longitude = last.longitude,
                                        description = "Near ${closest.name}"
                                    )
                                )
                            } else {
                                onSuccess(PAKISTAN_LOCATIONS[0]) // Committee Chowk, Jaranwala
                            }
                        }.addOnFailureListener {
                            onSuccess(PAKISTAN_LOCATIONS[0])
                        }
                    }
                }
                .addOnFailureListener { e ->
                    onError(e.message ?: "Failed to acquire GPS. Using Jaranwala default.")
                }
        } catch (e: Exception) {
            onError(e.message ?: "GPS service error")
        }
    }

    // Launch Google Maps navigation
    fun launchGoogleMapsNavigation(context: Context, destination: String, destLat: Double? = null, destLng: Double? = null) {
        try {
            val uri = if (destLat != null && destLng != null && isWithinPakistan(destLat, destLng)) {
                Uri.parse("google.navigation:q=$destLat,$destLng&mode=d")
            } else {
                val encoded = Uri.encode("$destination, Jaranwala, Punjab, Pakistan")
                Uri.parse("geo:31.3333,73.4333?q=$encoded")
            }
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
            }
            if (intent.resolveActivity(context.packageManager) != null) {
                context.startActivity(intent)
            } else {
                // Browser fallback
                val webUri = if (destLat != null && destLng != null) {
                    Uri.parse("https://www.google.com/maps/dir/?api=1&destination=$destLat,$destLng")
                } else {
                    Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode("$destination Pakistan")}")
                }
                context.startActivity(Intent(Intent.ACTION_VIEW, webUri))
            }
        } catch (_: Exception) {
            val fallback = Uri.parse("https://www.google.com/maps/search/?api=1&query=Jaranwala+Pakistan")
            context.startActivity(Intent(Intent.ACTION_VIEW, fallback))
        }
    }
}
