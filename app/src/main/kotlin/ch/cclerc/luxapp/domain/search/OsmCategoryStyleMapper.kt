package ch.cclerc.luxapp.domain.search

object OsmCategoryStyleMapper {

    private val FOOD = setOf(
        "restaurant", "cafe", "bar", "pub", "fast_food", "food_court", "biergarten",
        "ice_cream", "bakery", "pastry", "confectionery", "butcher", "deli", "cheese",
        "greengrocer", "supermarket", "convenience", "brewery", "winery", "distillery",
        "wine", "alcohol", "coffee", "marketplace"
    )

    private val AIRPORT = setOf("aerodrome", "terminal", "airport")

    private val LODGING = setOf(
        "hotel", "hostel", "motel", "guest_house", "apartment", "chalet",
        "camp_site", "caravan_site", "marina"
    )

    private val PARKING = setOf(
        "parking", "parking_entrance", "parking_space", "bicycle_parking", "motorcycle_parking"
    )

    private val HEALTH = setOf(
        "hospital", "pharmacy", "clinic", "doctors", "dentist", "nursing_home",
        "fitness_centre", "fitness_station", "gym", "spa", "sauna"
    )

    private val EDUCATION = setOf(
        "library", "school", "university", "college", "kindergarten", "planetarium",
        "language_school", "driving_school", "books"
    )

    private val CULTURE = setOf(
        "museum", "gallery", "theatre", "cinema", "nightclub", "casino", "arts_centre",
        "music_venue", "community_centre", "exhibition_centre"
    )

    private val HERITAGE = setOf(
        "castle", "fort", "city_gate", "monument", "memorial", "ruins", "archaeological_site",
        "attraction", "artwork", "viewpoint", "tower", "townhall", "manor", "church",
        "cathedral", "chapel", "place_of_worship", "monastery"
    )

    private val NATURE = setOf(
        "park", "garden", "nature_reserve", "dog_park", "common", "zoo", "theme_park",
        "amusement_park", "aquarium", "beach", "beach_resort", "wood", "forest", "peak",
        "picnic_site", "playground"
    )

    private val SPORT = setOf(
        "pitch", "sports_centre", "sports_hall", "stadium", "golf_course", "miniature_golf",
        "track", "ice_rink", "bowling_alley", "horse_riding", "skatepark", "climbing",
        "fitness", "baseball", "basketball", "soccer", "football", "tennis", "volleyball",
        "hiking", "skiing", "karting", "athletics"
    )

    private val WATER_SPORT = setOf(
        "swimming_pool", "water_park", "swimming", "swimming_area", "surfing",
        "kayaking", "canoe", "fishing", "diving", "sailing"
    )

    private val TRANSIT = setOf(
        "station", "halt", "tram_stop", "subway_entrance", "platform", "stop_position",
        "bus_stop", "bus_station", "ferry_terminal"
    )

    private val AUTOMOTIVE = setOf(
        "fuel", "charging_station", "car_rental", "car_sharing", "car_wash", "car_repair",
        "car", "car_parts", "motorcycle", "driving_range", "tyres"
    )

    private val FINANCE = setOf(
        "bank", "atm", "bureau_de_change", "post_office", "post_box", "money_transfer"
    )

    private val SAFETY = setOf("police", "fire_station", "prison", "ranger_station")

    private val BEAUTY = setOf("hairdresser", "beauty", "cosmetics", "perfumery", "nails", "tattoo")

    private val LAUNDRY = setOf("laundry", "dry_cleaning")

    private val RESTROOM = setOf("toilets", "shower", "changing_room")

    private val ANIMAL = setOf("veterinary", "pet", "pet_grooming", "animal_shelter", "animal_boarding")

    private val SHOP_KEYS = setOf("shop", "craft", "office")

    fun style(osmKey: String?, osmValue: String?): SearchResultVisualStyle {
        val key = osmKey?.lowercase().orEmpty()
        val value = osmValue?.lowercase().orEmpty()

        if (value in FOOD) return SearchResultVisualStyle("fork.knife", SearchResultVisualColor.YELLOW)
        if (key == "aeroway" || value in AIRPORT) {
            return SearchResultVisualStyle("airplane", SearchResultVisualColor.BLUE)
        }
        if (value in LODGING) return SearchResultVisualStyle("bed.double.fill", SearchResultVisualColor.TEAL)
        if (value in PARKING) {
            return SearchResultVisualStyle("parkingsign.circle.fill", SearchResultVisualColor.INDIGO)
        }
        if (value in ANIMAL) return SearchResultVisualStyle("pawprint.fill", SearchResultVisualColor.YELLOW)
        if (key == "healthcare" || value in HEALTH) {
            return SearchResultVisualStyle("cross.case.fill", SearchResultVisualColor.RED)
        }
        if (value in EDUCATION) return SearchResultVisualStyle("book.fill", SearchResultVisualColor.BROWN)
        if (value in CULTURE) {
            return SearchResultVisualStyle("theatermasks.fill", SearchResultVisualColor.PURPLE)
        }
        if (key == "historic" || value in HERITAGE) {
            return SearchResultVisualStyle("building.columns.fill", SearchResultVisualColor.BROWN)
        }
        if (value in WATER_SPORT) return SearchResultVisualStyle("water.waves", SearchResultVisualColor.BLUE)
        if (value in NATURE) return SearchResultVisualStyle("tree.fill", SearchResultVisualColor.GREEN)
        if (key == "sport" || value in SPORT) {
            return SearchResultVisualStyle("figure.outdoor.cycle", SearchResultVisualColor.GREEN)
        }
        if (key == "public_transport" || key == "railway" || value in TRANSIT) {
            return SearchResultVisualStyle("tram.fill", SearchResultVisualColor.MINT)
        }
        if (value in AUTOMOTIVE) return SearchResultVisualStyle("car.fill", SearchResultVisualColor.CYAN)
        if (value in FINANCE) {
            return SearchResultVisualStyle("building.columns.fill", SearchResultVisualColor.BLUE)
        }
        if (value in SAFETY) return SearchResultVisualStyle("shield.fill", SearchResultVisualColor.RED)
        if (value in BEAUTY) return SearchResultVisualStyle("sparkles", SearchResultVisualColor.PINK)
        if (value in LAUNDRY) return SearchResultVisualStyle("washer.fill", SearchResultVisualColor.CYAN)
        if (value in RESTROOM) return SearchResultVisualStyle("figure.stand", SearchResultVisualColor.GRAY)
        if (key in SHOP_KEYS) return SearchResultVisualStyle("building.2.fill", SearchResultVisualColor.BLUE)

        return SearchResultVisualStyle("mappin", SearchResultVisualColor.BLUE)
    }
}
