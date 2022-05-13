package agents;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.OneShotBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import model.Customer;
import services.CustomerService;

import java.util.ArrayList;
import java.util.List;

public class ClientAgent extends Agent {
    private static class RequestBehaviour extends Behaviour {
        private final AID [] sellers;
        private final Customer.Order order;

        private MessageTemplate template;
        private int step = 0;

        public RequestBehaviour (Agent agent, Customer.Order order, AID [] sellers) {
            super(agent);

            this.order = order;
            this.sellers = sellers;
        }

        @Override
        public void action () {
            switch (step) {
                case 0 -> {
                    ACLMessage message = new ACLMessage (ACLMessage.CFP);
                    for (AID seller : sellers) {
                        message.addReceiver (seller);
                    }

                    String replyWith = "cfp-" + System.currentTimeMillis ();

                    message.setContent (order.serialize ());
                    message.setConversationId ("food-order");
                    message.setReplyWith (replyWith); // set unique identifier for this conversation

                    getAgent ().send (message);

                    template = MessageTemplate.and (MessageTemplate.MatchConversationId ("food-order"), MessageTemplate.MatchInReplyTo (replyWith));
                    step = 1;
                }

                default -> {
                    step = 0;
                }
            }
        }

        @Override
        public boolean done () {
            return false;
        }
    };

    private Customer customer = null;

    @Override
    protected void setup () {
        Object [] args = getArguments ();
        String customerId = (String) args [0];

        customer = CustomerService.getInstance ().getCustomer (customerId);

        // every 4s customer makes their next pre programmed order
        Behaviour orderBehaviour = new TickerBehaviour (this, 4000) {
            private int nextOrder = 0;

            @Override
            protected void onTick () {
                List<Customer.Order> orders = customer.getOrders ();
                if (nextOrder < orders.size ()) {
                    // get the order data
                    Customer.Order order = orders.get (nextOrder);
                    String cuisineId = Integer.toString (order.dish () / 100);

                    // make the order here
                    DFAgentDescription template = new DFAgentDescription ();
                    ServiceDescription serviceDescription = new ServiceDescription ();

                    serviceDescription.setType ("cuisine" + cuisineId);
                    template.addServices (serviceDescription);

                    try {
                        DFAgentDescription [] result = DFService.search (getAgent (), template);
                        ACLMessage cfp = new ACLMessage (ACLMessage.CFP);

                        for (DFAgentDescription dfAgentDescription : result) {
                            System.out.printf ("%s will try to order %d from %s\n", getName (), order.dish (), dfAgentDescription.getName ());
                            cfp.addReceiver (dfAgentDescription.getName ());
                        }

                        cfp.setContent ("order:" + order.dish () + ":" + order.amount ());
                        send (cfp);
                    } catch (FIPAException exception) {
                        System.err.printf ("failed to order %d\n", order.dish ());
                        exception.printStackTrace ();
                    }

                    nextOrder++;
                } else {
                    removeBehaviour (this);
                }
            }
        };

        System.out.printf ("a new client with name %s for %s was created\n", getName (), customer.getName ());

        addBehaviour (orderBehaviour);
        super.setup ();
    }
}
