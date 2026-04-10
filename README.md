# davyie-weather-app
This repository is part of an assignment. 

# Assignment
We need to create two GET endpoints. 

A list of the user’s favourite locations where the temperature will be above a certain temperature the next
day. It is not required to store the user’s favorites server side - the client will pass those as part of the
request.
```
GET | /weather/summary?unit=<celsius|fahrenheit>temperature=<int>&locations=<location ids
separated by comma>
```

A list of temperatures for the next 5 days in one specific location.
```
GET | /weather/locations/<location_id>
```

# Software 
- Spring boot 4.0 
- Java 21 
- Maven 3.9 
- Docker 28.5.1

# How to build 

Run the following commands
```markdown
For HTTPS:
git clone https://github.com/davyie/davyie-weather-app.git
```
```markdown
For SSH
git clone git@github.com:davyie/davyie-weather-app.git
```

```markdown
cd git@github.com:davyie/davyie-weather-app.git
```

```markdown
mvn clean install
```
followed by 
```markdown
mvn spring-boot:build-image
```
This will build images from the project. 
To run the services we have to run the command 
```markdown
cd docker && docker compose up -d
```
This will run all the services on docker containers. 

# Approach 
Controller - Holds two endpoints for each of the endpoints in the assignment description. 
Service - The methods which makes the call with client. 
Client - The class which sends requests towards OpenWeather API. 
Models - All the classes which core.models the problem and responses from Open Weather. 

Asynchronous programming with Mono and Flux to support multiple calls to Open Weather API without blocking the main thread.

## Rate limit 
The first idea is to implement rate limit on our api. The second idea is to implement rate limit on 
3rd party api. We can do this in multiple ways, either through memory or redis. We have chosen 
to do this using redis because we want to have a centralized storage for our data. This can be 
extended to support distributed system as well meaning that if we were to have multiple instances 
of our application running and a load balancer to distribute our requests then we can have a unified 
tracking of the number of requests from a specific client, i.e., if a client sends multiple requests and 
the requests are distributed between our instances we can still keep track of how many requests that 
particular client has made. 

### How it works 
The ratelimiter is applied as a filter. The client request gets passed through multiple filters before it 
hits our controller, meaning that we can intercept the request and analyse the contents or perform actions like in 
this case we check how many requests the client has sent to our server. 

The ratelimiter is implemented as a provider which holds the implementation of the rate limit filter and service. 
The rate limit filter implements `Filter` interface meaning that Spring will recognize our filter and apply it to the filter chain. 
During the passthrough of the filter we perform logic to check if rate limit is exceed or not. To help us keep
track of the number of requests we are using `Bucket4J` library which has an algorithm of how the number of requests should be handled. 

```markdown
Side note
Bucket algorithm is applied to limit access to resources by considering how many accesses has been made during 
a period of time and refill the bucket. Think of it like a leaking bucket of water. If the water runs out then 
we are not allowed to access it anymore. Then we have to refill the bucket so we can access it again. The parameters 
of the algorithm is `amount of water`, `the outflow of water`, `refill interval` and `refill amount`. This 
looks a lot like a differential equation in my opinion which I find funny and amusing.
```

Once the filter was implemented we created a service to hold the logic of how we should use `Bucket4j`. This is used in the `Filter` class to 
enforce our rate limit strategy. 

We also implement a second part of rate limit to monitor external API calls. This was done by adding another method to 
service class and store a counter on redis to keep track of how many requests we have made towards 3rd party API. This was later used in 
`openweather-adapter`.

### Starter
This is a starter package which can be used to include rate limit package to other projects.
It works by utilising `Spring AutoConfiguration` and `Bean` creation and `configuration` properties. 
By create a `AutoConfiguration` class we can declare what beans are created. That includes our service bean 
and all the supporting beans such as `Filter` and `Redis` objects such as `ProxyManager` and `ReactiveRedisTemplate`. 
Then we create a properties file to hold all the configurable values which are injected into the `AutoConfiguration` file 
to instantiate the objects according to a configuration without having to rebuild the project. 

## Hexagonal
The three main parts of hexagonal pattern are `core` - the domain/application logic, `ports` - the interfaces and `adapater` - the implementations.
This was utilised to build the connection between our API and 3rd party API. By utilising `Interface` we 
can create the communication layer between the two parts. We also want them to communicate over 
a common domain langauge which is the `core`. The `core` contains the entities, business rules and use cases. 
It holds the internal representation of the data. Then we have the `adapters` which implements the core. 
By implementing the interfaces in `core` we are able to swap out an implementation if needed. 
 
## Spring boot starters & Autoconfiguration

## Cache-aside pattern 
This is a pattern to support millions of users with the limitation of being able to call 3rd party APi.
By store the data from 3rd party api we are able to supply users without making API call every single time. 
We have a cache and when we receive a request we check if the data for the request is present in our cache. 
If it is present we return it otherwise we make the API call and store the response so we can reuse the 
response if a similar request is received in the future. We have a TTL for the cache which means that data 
is stored for a period of time before it gets refreshed. Because we are working with weather data, we are able to store 
the data for longer period of time because it is unlikely that the forecast will drastically change between hours of the day. 

## Separation of Concern 
This is the principle of doing one thing in one module/project. This is so we don't get spaghetti code and dependencies everywhere. 
Hexagonal pattern helps us with this because we separate the implementation from rest. We have adapter, core and our api. 
Because our API and adapter agree on the `core` we can make the communicate with each other through the interfaces. 
This separates the concerns to its own module. The implementation is its own module which can be implemented in multiple ways.

## Single Responsibility Principle
This is part of `SOLID`. This is applied to individual building blocks of a project. The idea is that 
`a class should have one, and only one reason to change`. 

## Testing 

## Maven Formatter  
We are using `spotless` which is a plugin for maven which can check formatting and also fix it when we running the following commands 
`mvn spotless:check` and `mvn spotless:apply` respectively. 

## Docker 

## Continuous Integration 