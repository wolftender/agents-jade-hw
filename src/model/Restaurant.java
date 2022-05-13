package model;

import java.util.*;

public class Restaurant {
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
