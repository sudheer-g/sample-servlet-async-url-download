# Demo Async and NIO Servlets

These are Demos for the Async and Non Blocking I/O features in Java's Servlet 3.1 API. The servlets 
download(GET) a URL asynchronously and non blocking ways respectively.

## Getting Started

Clone this repository.
```
git clone https://github.com/sudheer-g/sample-servlet-async-url-download.git
```

### Prerequisites

Make sure you have these installed on your machine.
```
Apache Maven
Apache Tomcat  
Java 8
```
#####This project was deployed in Tomcat 9. Other versions have not been tested.


## Deployment

To build this project with maven:
```
mvn clean install
```
Copy the resulting WAR from the generated target directory to the tomcat /webapps 
folder and start/restart tomcat.

Hit the following URLs in the browser:

##### For the Async Demo
```
http://localhost:8080/SampleAsyncServlet/async?url=www.example.com
```

##### For the NIO Demo
```
http://localhost:8080/SampleAsyncServlet/nonBlocking?url=www.example.com
```

## Acknowledgments

* This README's template was taken from [PurpleBooth/README-Template.md](https://gist.github.com/PurpleBooth/109311bb0361f32d87a2#file-readme-template-md)

