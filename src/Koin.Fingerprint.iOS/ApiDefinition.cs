using System;
using ObjCRuntime;
using Foundation;
using UIKit;

namespace Fingerprint.iOS.Binding
{
    // @interface Fingerprint : NSObject
    [BaseType (typeof(NSObject))]
    interface Fingerprint
    {
        // +(void)registerWithOrganizationId:(NSString * _Nonnull)organizationId;
        [Static]
        [Export ("registerWithOrganizationId:")]
        void RegisterWithOrganizationId (string organizationId);

        // +(void)registerWithOrganizationId:(NSString * _Nonnull)organizationId url:(NSString * _Nonnull)url;
        [Static]
        [Export ("registerWithOrganizationId:url:")]
        void RegisterWithOrganizationId (string organizationId, string url);

        // +(void)profileWithSessionId:(NSString * _Nonnull)sessionId;
        [Static]
        [Export ("profileWithSessionId:")]
        void ProfileWithSessionId (string sessionId);
    }
}