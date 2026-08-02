import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import Stripe from 'https://esm.sh/stripe@12.0.0?target=deno'

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') {
    return new Response('ok', { headers: corsHeaders })
  }

  try {
    const { amount, currency, description } = await req.json()

    // Get Secret Key from environment variables
    let stripeSecretKey = Deno.env.get('STRIPE_SECRET_KEY')
    if (!stripeSecretKey) {
      // Fallback for development if secret is not set in Supabase dashboard
      stripeSecretKey = 'sk_test_51Tt9NCLlCDwDo6FpA9O5cNwoLAiEHPETWZ6wjMTuHcNjIlk616dG87KcXbwEXVhbGLgYG1Mj47I0rHMhMcYQpObp00gyJBxKUy'
    }

    const stripe = new Stripe(stripeSecretKey, {
      httpClient: Stripe.createFetchHttpClient(),
    })

    const paymentIntent = await stripe.paymentIntents.create({
      amount,
      currency: currency || 'lkr',
      description: description || 'Smart Farmer Payment',
    })

    return new Response(
      JSON.stringify({ client_secret: paymentIntent.client_secret }),
      {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 200,
      }
    )
  } catch (error) {
    return new Response(
      JSON.stringify({ error: error.message }),
      {
        headers: { ...corsHeaders, 'Content-Type': 'application/json' },
        status: 400,
      }
    )
  }
})
