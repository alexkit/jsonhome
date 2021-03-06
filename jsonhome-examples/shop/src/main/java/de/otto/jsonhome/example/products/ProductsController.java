/*
 * Copyright 2012 Guido Steinacker
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.otto.jsonhome.example.products;

import de.otto.jsonhome.annotation.Doc;
import de.otto.jsonhome.annotation.Docs;
import de.otto.jsonhome.annotation.Hints;
import de.otto.jsonhome.annotation.Rel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ConcurrentModificationException;
import java.util.List;
import java.util.Map;

import static de.otto.jsonhome.example.products.ProductConverter.*;
import static de.otto.jsonhome.model.Precondition.ETAG;
import static java.util.Collections.singletonMap;
import static javax.servlet.http.HttpServletResponse.SC_CREATED;
import static javax.servlet.http.HttpServletResponse.SC_OK;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.PRECONDITION_FAILED;


/**
 * Controller used to handle the product-related resources of our example web-shop application.
 * @author Guido Steinacker
 * @since 15.09.12
 */
@Controller
@RequestMapping(value = "/products")
@Docs({
        @Doc(value = {"The collection of products.",
                      "This is a second paragraph, describing the collection of products."},
             include = "/rel/products.md",
             rel = "/rel/products"),
        @Doc(value = "A link to a single product.",
             rel = "/rel/product",
             link = "http://de.wikipedia.org/wiki/Produkt_(Wirtschaft)"),
        @Doc(value = "Links to a resource used to create a product.",
             rel = "/rel/product/form")
})
public class ProductsController {

    @Autowired
    private ProductService productService;

    @RequestMapping(
            method = RequestMethod.GET,
            produces = "text/html"
    )
    @Rel("/rel/products")
    public ModelAndView getProductsHtml(final @RequestParam(required = false, defaultValue = "*") String query) {
        final List<Product> products = productService.findProducts(query);
        return new ModelAndView("products", singletonMap("products", products));
    }

    @RequestMapping(
            method = RequestMethod.GET,
            produces = {"application/example-products", "application/json"}
    )
    @ResponseBody
    @Rel("/rel/products")
    public Map<String, ?> getProductsJson(final @RequestParam(required = false, defaultValue = "*") String query,
                                          final HttpServletRequest request) {
        final List<Product> products = productService.findProducts(query);
        return productsToJson(products, request.getContextPath());
    }

    @RequestMapping(
            method = RequestMethod.POST,
            consumes = "application/x-www-form-urlencoded",
            produces = "text/html"
    )
    @Rel("/rel/product/form")
    public ModelAndView postProductUrlEncoded(final @RequestParam String title,
                                              final @RequestParam String price,
                                              final HttpServletRequest request,
                                              final HttpServletResponse response) {
        // Create a new product with id generated by the service:
        final long id = productService.addProduct(title, price);
        final List<Product> products = productService.findProducts("*");
        // Return the URI of the created product in Location header:
        response.setHeader("Location", request.getContextPath() + "/products/" + id);
        response.setStatus(SC_CREATED);
        return new ModelAndView("products", singletonMap("products", products));
    }

    @RequestMapping(
            value = "/{productId}",
            method = RequestMethod.GET,
            produces = "text/html"
    )
    @Rel("/rel/product")
    public ModelAndView getProductAsHtml(@Doc({"The unique identifier of the requested product.",
                                               "A second line of valuable documentation."})
                                         @PathVariable final long productId) {
        final Product product = productService.findProduct(productId);
        return new ModelAndView("product", singletonMap("product", product));
    }

    @RequestMapping(
            value = "/{productId}",
            method = RequestMethod.GET,
            produces = {"application/example-product", "application/json"}
    )
    @ResponseBody
    @Rel("/rel/product")
    public Map<String, Object> getProductAsJson(final @PathVariable long productId,
                                                final HttpServletRequest request) {
        return productToJson(productService.findProduct(productId), request.getContextPath());
    }

    @RequestMapping(
            value = "/{productId}",
            method = RequestMethod.PUT,
            consumes = {"application/example-product", "application/json"},
            produces = {"application/example-product", "application/json"}
    )
    @ResponseBody
    @Rel("/rel/product")
    @Hints(preconditionReq = ETAG)
    public Map<String, ?> putProduct(final @PathVariable long productId,
                                     final @RequestBody Map<String, String> document,
                                     final HttpServletRequest request,
                                     final HttpServletResponse response) throws IOException {
        final String expectedETag = request.getHeader("If-Match");
        final Product expected = productService.findProduct(productId);
        if (expected == null || expectedETag.equals("*") || expected.getETag().equals(expectedETag)) {
            final Product product = jsonToProduct(productId, document);
            final Product previous = productService.createOrUpdateProduct(product, expected);
            response.setStatus(previous == null ? SC_CREATED : SC_OK);
            return productToJson(product, request.getContextPath());
        } else {
            throw new ConcurrentModificationException("product was concurrently modified.");
        }
    }

    @ResponseStatus(value = BAD_REQUEST)
    @ExceptionHandler({IllegalArgumentException.class})
    public void handleNotFound() throws IOException {
    }

    @ResponseStatus(value = PRECONDITION_FAILED)
    @ExceptionHandler({ConcurrentModificationException.class})
    public void handleConcurrentModification() throws IOException {
    }
}
