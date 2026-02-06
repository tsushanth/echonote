import CoreLocation
import Foundation

@Observable
final class LocationService: NSObject, CLLocationManagerDelegate {
    private let locationManager = CLLocationManager()
    private let geocoder = CLGeocoder()

    var currentLocationName: String?
    var authorizationStatus: CLAuthorizationStatus = .notDetermined
    private var locationContinuation: CheckedContinuation<String?, Never>?

    override init() {
        super.init()
        locationManager.delegate = self
        locationManager.desiredAccuracy = kCLLocationAccuracyHundredMeters
        authorizationStatus = locationManager.authorizationStatus
    }

    func requestPermission() {
        locationManager.requestWhenInUseAuthorization()
    }

    func getCurrentLocationName() async -> String? {
        guard authorizationStatus == .authorizedWhenInUse || authorizationStatus == .authorizedAlways else {
            return nil
        }

        return await withCheckedContinuation { continuation in
            self.locationContinuation = continuation
            self.locationManager.requestLocation()
        }
    }

    func locationManager(_ manager: CLLocationManager, didUpdateLocations locations: [CLLocation]) {
        guard let location = locations.last else {
            locationContinuation?.resume(returning: nil)
            locationContinuation = nil
            return
        }

        geocoder.reverseGeocodeLocation(location) { [weak self] placemarks, error in
            guard let self = self else { return }

            if let placemark = placemarks?.first {
                let name = placemark.name
                    ?? placemark.locality
                    ?? placemark.administrativeArea
                    ?? "Unknown Location"
                self.currentLocationName = name
                self.locationContinuation?.resume(returning: name)
            } else {
                self.locationContinuation?.resume(returning: nil)
            }
            self.locationContinuation = nil
        }
    }

    func locationManager(_ manager: CLLocationManager, didFailWithError error: Error) {
        print("Location error: \(error.localizedDescription)")
        locationContinuation?.resume(returning: nil)
        locationContinuation = nil
    }

    func locationManagerDidChangeAuthorization(_ manager: CLLocationManager) {
        authorizationStatus = manager.authorizationStatus
    }
}
