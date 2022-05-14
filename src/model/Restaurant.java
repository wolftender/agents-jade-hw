package model;

import java.util.*;

public class Restaurant {
    public static record Offer (int dish, int amount, float price) {
        public String serialize () {
            return String.format ("offer:%d:%d:%f", dish, amount, price);
        }

        public static Offer deserialize (String str) {
            String [] parts = str.split (":");

            if (parts.length != 4) {
                return null;
            }

            if (!parts [0].equals ("offer")) {
                return null;
            }

            int dish = Integer.parseInt (parts [1]);
            int amount = Integer.parseInt (parts [2]);
            float price = Float.parseFloat (parts [3]);

            return new Offer (dish, amount, price);
        }
    }

    private final Map<Integer, Float> dishesServed;
    private final List<Integer> cuisinesAdvertised;

    private final String name;

    private final int maxPeople;
    private final int reliability;

    public List<Integer> getServedDishes () {
        return new ArrayList<> (dishesServed.keySet ());
    }

    public List<Integer> getCuisinesAdvertised () {
        return new ArrayList<> (cuisinesAdvertised);
    }

    public Iterator<Integer> getCuisineIterator () {
        return cuisinesAdvertised.iterator ();
    }

    public Iterator<Integer> getDishIterator () {
        return dishesServed.keySet ().iterator ();
    }

    public boolean serves (int dish) {
        return dishesServed.containsKey (dish);
    }

    public int getReliability () {
        return reliability;
    }

    public Float price (int dish, int people) {
        if (people > maxPeople) {
            return null;
        }

        if (serves (dish)) {
            return dishesServed.get (dish) * people;
        }

        return null;
    }

    public String getName () {
        return name;
    }

    public Restaurant (String name, int maxPeople, int reliability) {
        this.name = name;
        this.maxPeople = maxPeople;
        this.reliability = reliability;

        dishesServed = new HashMap<> ();
        cuisinesAdvertised = new ArrayList<> ();
    }

    public void addDish (int dish, float price) {
        int cuisineId = dish / 100;

        cuisinesAdvertised.add (cuisineId);
        dishesServed.put (dish, price);
    }
}
