package services;

import model.Restaurant;

import java.util.HashMap;
import java.util.Map;

/*
 * this is a very simple class to hold restaurant models for simulation
 * its not related to how agents work, it just stores data about restaurants from the xml file
 */

public final class RestaurantService {
    private static RestaurantService instance = null;
    public static RestaurantService getInstance () {
        if (instance == null) {
            instance = new RestaurantService ();
        }

        return instance;
    }

    private final Map<String, Restaurant> restaurants;

    private RestaurantService () {
        restaurants = new HashMap<> ();
    }

    public void registerRestaurant (String id, Restaurant restaurant) {
        restaurants.put (id, restaurant);
    }

    public Restaurant getRestaurant (String id) {
        return restaurants.get (id);
    }
}
