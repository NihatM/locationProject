let map;
let markers = [];
let markerClusterer = null;
let allMarkerData = [];
let foundMarkers = [];
let currentIndex = 0;
let isSearching = false;
let isManagingVisibility = false;


const navEl = document.getElementById("nav-mobile-menu");
const nav = document.querySelector("nav");
const loadingOverlay = document.getElementById("loadingOverlay");
const searchButton = document.getElementById("search-button");
const searchInput = document.getElementById("search-input");
let lastOpenedInfoWindow = null;
const infoMessage = document.getElementById("info-message");

if (navEl) navEl.addEventListener("click", () => nav.classList.toggle("active"));
searchButton.addEventListener("click", handleSearch);
searchInput.addEventListener("input", debounce(handleSearch, 300));
if (infoMessage) infoMessage.addEventListener("click", resetMarkers);

/**
 * marker fetchi və xəritə
 */
async function initMap() {
    const mapOptions = {
        center: { lat: 39.39048, lng: 46.15494 },
        zoom: 10,
        mapTypeId: 'hybrid',
        tilt: 45,
        gestureHandling: "cooperative",
        heading: 90,
        mapTypeControl: true,
        streetViewControl: false,
        fullscreenControl: true,
        fullscreenControlOptions: { position: google.maps.ControlPosition.RIGHT_CENTER },
        styles: [
            { featureType: "all", elementType: "labels", stylers: [{ visibility: "off" }] },
            { featureType: "administrative.country", elementType: "labels", stylers: [{ visibility: "on" }] }
        ]
    };

    map = new google.maps.Map(document.getElementById("map"), mapOptions);

    try {
        await fetchMarkersAndDisplay();
        map.addListener('idle', () => {
            if (!isSearching && !isManagingVisibility) manageMarkerVisibility();
        });
    } catch (error) {
        console.error("Xəta:", error);
        alert("xəritənin yüklənməsində xəta var. Zəhmət olmasa bizimlə əlaqə saxlayın və yaxud bir az sonra yenidən dənəyin");
    }
}

/**
 * api fetch - only store data, don't create marker objects
 */
async function fetchMarkersAndDisplay() {
    toggleLoading(true);

    try {
        const response = await fetch('https://qerbiazerbaycanim.com/api/v1/markers/all', { headers: { 'Content-Type': 'application/json' } });
        if (!response.ok) throw new Error(`HTTP error! Status: ${response.status}`);

        const data = await response.json();
        markers.forEach(marker => marker.setMap(null));
        markers = [];
        allMarkerData = data;
        const markerCountEl = document.getElementById("marker-count");
        if (markerCountEl) markerCountEl.textContent = allMarkerData.length.toLocaleString();
        if (markerClusterer) {
            markerClusterer.clearMarkers();
            markerClusterer = null;
        }
        manageMarkerVisibility();
    } catch (error) {
        console.error("Error fetching markers:", error);
        alert("Bağlantı xətası. Zəhmət olmasa səhifəni yeniləyin!");
    } finally {
        toggleLoading(false);
    }
}

/**
 * Create a single marker object from data
 */
function createMarkerFromData(markerData) {
    const mapMarker = new google.maps.Marker({
        position: { lat: markerData.latitude, lng: markerData.longitude },
        map: map,
        title: markerData.title,
        label: {
            text: markerData.title || 'Invalid Title',
            color: '#ffffff',
            fontSize: '14px',
            fontWeight: '500',
            className: 'marker-label'
        },
        icon: getIcon(markerData.type || markerData.markerType),
        visible: true
    });

    const originalDescription = markerData.description;
    const translations = markerData.translations || [];

    const infoWindow = new google.maps.InfoWindow({
        content: `<div class="custom-info-window"><strong>${markerData.title}</strong><p>${originalDescription}</p></div>`
    });

    mapMarker.addListener("click", () => {
        if (lastOpenedInfoWindow) lastOpenedInfoWindow.close();
        infoWindow.open(map, mapMarker);
        lastOpenedInfoWindow = infoWindow;
    });

    mapMarker.customData = {
        id: markerData.id,
        title: markerData.title,
        originalDescription: originalDescription,
        translations: translations,
        infoWindow: infoWindow,
        type: markerData.type || markerData.markerType
    };

    return mapMarker;
}

/**
 * Only create markers for the current viewport - massive performance improvement
 */
function manageMarkerVisibility() {
    if (isManagingVisibility) return;
    isManagingVisibility = true;
    const bounds = map.getBounds();
    const currentZoom = map.getZoom();
    if (!bounds || !allMarkerData.length) { isManagingVisibility = false; return; }

    markers.forEach(marker => marker.setMap(null));
    markers = [];
    if (markerClusterer) {
        markerClusterer.clearMarkers();
        markerClusterer = null;
    }

    const visibleData = allMarkerData.filter(md => {
        const pos = new google.maps.LatLng(md.latitude, md.longitude);
        const type = (md.type || md.markerType || '').toLowerCase();

        if (currentZoom >= 15) {
            return bounds.contains(pos);
        } else if (currentZoom >= 12 && currentZoom < 18) {
            return (type === 'building' || type === 'village') && bounds.contains(pos);
        } else if (currentZoom < 12) {
            return (type === 'city' || type === 'region') && bounds.contains(pos);
        }
        return false;
    });

    visibleData.forEach(md => {
        const marker = createMarkerFromData(md);
        markers.push(marker);
    });

    if (markers.length > 50 && window.markerClusterer) {
        markerClusterer = new window.markerClusterer.MarkerClusterer({
            map: map,
            markers: markers
        });
    }
    isManagingVisibility = false;
}

function getLocalizedTypeLabel(type) {
    switch (type.toLowerCase()) {
        case 'building': return 'Kənd';
        case 'region': return 'Rayon';
        case 'village': return 'Xaraba';
        case 'city': return 'Şəhər';
        default: return 'Yer adı';
    }
}

function capitalizeFirstLetter(string) {
    return string.charAt(0).toUpperCase() + string.slice(1);
}

function renderSearchResults(results) {
    const container = document.getElementById("search-results");
    container.innerHTML = "";

    if (results.length === 0) {
        container.style.display = "none";
        return;
    }

    results.forEach((marker, index) => {
        const type = getMarkerType(marker);
        const iconUrl = getIcon(type);

        const item = document.createElement("div");
        item.className = "search-result-item";
        item.innerHTML = `
            <div style="display: flex; align-items: center;">
                <img src="${iconUrl}" alt="${type}" style="width: 20px; height: 20px; margin-right: 10px;">
                <div>
                    <div style="font-weight: 500;">${marker.getTitle()}</div>
                    <div style="font-size: 12px; color: #666;">${getLocalizedTypeLabel(type)}</div>
                </div>
            </div>
        `;

        item.addEventListener("click", () => {
            currentIndex = index;
            focusOnMarker(marker);
            document.getElementById("search-results").style.display = "none";
            const si = document.getElementById("search-input");
            if (si) si.value = "";
        });

        container.appendChild(item);
    });

    container.style.display = "block";
}


function handleSearch() {
    const searchText = searchInput.value.toLowerCase().trim();
    isSearching = !!searchText;

    if (!searchText) {
        resetMarkers();
        infoMessage.style.display = "none";
        return;
    }
    infoMessage.textContent = 'Digər pinləri göstər';
    infoMessage.style.display = "block";

    markers.forEach(marker => marker.setMap(null));
    markers = [];
    if (markerClusterer) {
        markerClusterer.clearMarkers();
        markerClusterer = null;
    }

    const matchingData = allMarkerData
        .filter(md => (md.title || '').toLowerCase().includes(searchText))
        .sort((a, b) => {
            const priority = { city: 1, region: 2, building: 3, village: 4 };
            const typeA = (a.type || a.markerType || '').toLowerCase();
            const typeB = (b.type || b.markerType || '').toLowerCase();
            return (priority[typeA] || 99) - (priority[typeB] || 99);
        });

    foundMarkers = [];
    matchingData.forEach(md => {
        const marker = createMarkerFromData(md);
        foundMarkers.push(marker);
        markers.push(marker);
    });

    if (foundMarkers.length > 0) {
        currentIndex = 0;
        focusOnMarker(foundMarkers[currentIndex]);
    }
    renderSearchResults(foundMarkers);
}

function resetMarkers() {
    isSearching = false;
    searchInput.value = "";
    foundMarkers = [];
    infoMessage.style.display = "none";
    document.getElementById("search-results").style.display = "none";
    hideNavigationButtons();
    markers.forEach(marker => marker.setMap(null));
    markers = [];
    manageMarkerVisibility();
}

function focusOnMarker(marker) {
    if (marker && marker.getPosition) {
        map.setCenter(marker.getPosition());
        map.setZoom(15);
        if (marker.getTitle()) {
            history.replaceState(null, '', '#place=' + encodeURIComponent(marker.getTitle()));
        }
    }
}

function navigateMarkers(step) {
    if (foundMarkers.length > 1) {
        currentIndex = (currentIndex + step + foundMarkers.length) % foundMarkers.length;
        markers.forEach(marker => marker.setMap(null));
        const markerToFocus = foundMarkers[currentIndex];
        markerToFocus.setMap(map);
        markerToFocus.setVisible(true);
        focusOnMarker(markerToFocus);
    }
}

function hideNavigationButtons() {}

function toggleLoading(show) {
    if (loadingOverlay) loadingOverlay.style.display = show ? 'flex' : 'none';
}

function getIcon(type) {
    const markerIcons = {
        restaurant: '/images/icons/res.svg',
        building: '/images/icons/building.svg',
        road: '/images/icons/road.svg',
        store: '/images/icons/store.svg',
        park: '/images/icons/park.svg',
        river: '/images/icons/river.svg',
        lake: '/images/icons/lake.svg',
        village: '/images/icons/xaraba.svg',
        region: '/images/icons/region.svg',
        city: '/images/icons/city.svg',
        default: '/images/icons/pin.svg'
    };
    return markerIcons[type.toLowerCase()] || markerIcons.default;
}

function getMarkerType(marker) {
    const icon = marker.getIcon();
    const markerIcons = {
        restaurant: '/images/icons/res.svg',
        building: '/images/icons/building.svg',
        road: '/images/icons/road.svg',
        store: '/images/icons/store.svg',
        park: '/images/icons/park.svg',
        river: '/images/icons/river.svg',
        lake: '/images/icons/lake.svg',
        village: '/images/icons/xaraba.svg',
        region: '/images/icons/region.svg',
        city: '/images/icons/city.svg',
        default: '/images/icons/pin.svg'
    };
    return Object.keys(markerIcons).find(type => markerIcons[type] === icon) || 'other';
}

function debounce(func, delay) {
    let timerId;
    return function () {
        const context = this;
        const args = arguments;
        clearTimeout(timerId);
        timerId = setTimeout(() => {
            func.apply(context, args);
        }, delay);
    };
}

searchInput.addEventListener("focus", () => {
    if (foundMarkers.length > 0) {
        document.getElementById("search-results").style.display = "block";
    }
});

// Check URL hash for shareable place link
function checkHashPlace() {
    const hash = window.location.hash;
    if (hash.startsWith('#place=')) {
        const placeName = decodeURIComponent(hash.substring(7));
        if (placeName && searchInput) {
            searchInput.value = placeName;
            // Wait for map and data to load, then search
            const waitForData = setInterval(() => {
                if (allMarkerData.length > 0) {
                    clearInterval(waitForData);
                    handleSearch();
                }
            }, 500);
        }
    }
}
window.addEventListener('load', checkHashPlace);

window.initMap = initMap;
if (window.google && window.google.maps) {
    initMap();
}
