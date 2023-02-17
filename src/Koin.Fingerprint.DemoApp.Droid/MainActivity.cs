using System;
using Android.App;
using Android.Content.PM;
using Android.Runtime;
using Android.Views;
using Android.Widget;
using Android.OS;
using Com.Despegar.Fingerprintsdk;

namespace Koin.Fingerprint.DemoApp.Droid
{
    [Activity(Label = "TestFingerprintBindingSDK", Theme = "@style/MainTheme", MainLauncher = true,
        ConfigurationChanges = ConfigChanges.ScreenSize | ConfigChanges.Orientation)]
    public class MainActivity : global::Xamarin.Forms.Platform.Android.FormsAppCompatActivity
    {
        protected override void OnCreate(Bundle savedInstanceState)
        {
            TabLayoutResource = Resource.Layout.Tabbar;
            ToolbarResource = Resource.Layout.Toolbar;
            base.OnCreate(savedInstanceState);
            global::Xamarin.Forms.Forms.Init(this, savedInstanceState);
            LoadApplication(new App());

            try
            {
                DeviceFingerprint.Instance.Register("testOrganizationId", "https://adda-2800-a4-13cb-f000-41b3-6f2e-34cf-97c6.ngrok.io/Fingerprint");
                var sessionId = DeviceFingerprint.Instance.Profile(ApplicationContext);
            }
            catch (Exception ex)
            {

            }
        }
    }
}