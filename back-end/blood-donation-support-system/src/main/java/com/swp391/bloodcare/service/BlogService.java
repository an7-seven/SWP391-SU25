package com.swp391.bloodcare.service;

import com.swp391.bloodcare.dto.BlogDTO;
import com.swp391.bloodcare.entity.Account;
import com.swp391.bloodcare.entity.Blog;
import com.swp391.bloodcare.repository.AccountRepository;
import com.swp391.bloodcare.repository.BlogRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class BlogService {

    private final BlogRepository blogRepository;
    private final AccountRepository accountRepository;

    public BlogService(BlogRepository blogRepository, AccountRepository accountRepository) {
        this.blogRepository = blogRepository;
        this.accountRepository = accountRepository;
    }

    public BlogDTO getBlogById(String blogId) {
        Blog blog = blogRepository.findBlogByBlogId(blogId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy Blog với ID: " + blogId));
        return BlogDTO.toDTO(blog);
    }

    public List<BlogDTO> getLatestBlogs() {
        List<Blog> latestBlogs = blogRepository.findTop5ByOrderByPostDateDesc();
        return latestBlogs.stream()
                .map(BlogDTO::toDTO)
                .collect(Collectors.toList());
    }


    public List<BlogDTO> getAllBlogs() {
        return blogRepository.findAll().stream()
                .map(BlogDTO::toDTO)
                .toList();
    }

    public BlogDTO updateBlog(String blogId, BlogDTO dto) {
        Blog blog = blogRepository.findBlogByBlogId(blogId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy Blog để cập nhật"));

        if (dto.getContent() != null && !dto.getContent().isBlank())
            blog.setContent(dto.getContent());


        if (dto.getTagName() != null)
            blog.setTagName(dto.getTagName());

        return BlogDTO.toDTO(blogRepository.save(blog));
    }

    public BlogDTO deleteBlog(String blogId) {
        Blog blog = blogRepository.findBlogByBlogId(blogId)
                .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy Blog để xoá"));
        blogRepository.delete(blog);
        return BlogDTO.toDTO(blog);
    }

    public BlogDTO createBlogByUserName(BlogDTO dto, String accountId, MultipartFile thumbnail) {
        Blog blog = BlogDTO.toEntity(dto);
        blog.setBlogId(generateUniqueBlogId());
        blog.setPostDate(new Date());

        if (thumbnail != null && !thumbnail.isEmpty()) {
            try {
                String originalName = thumbnail.getOriginalFilename();
                String ext = originalName != null && originalName.contains(".")
                        ? originalName.substring(originalName.lastIndexOf("."))
                        : ".jpg";
                String fileName = UUID.randomUUID() + ext;

                // Upload folder ngoài target, không bị xoá khi build lại
                String uploadDir = System.getProperty("user.dir") + "/uploads/blog-thumbnails/";
                File uploadPath = new File(uploadDir);
                if (!uploadPath.exists()) {
                    boolean created = uploadPath.mkdirs();
                    if (!created) {
                        throw new IllegalStateException("Không thể tạo thư mục upload tại " + uploadPath.getAbsolutePath());
                    }
                }

                File savedFile = new File(uploadPath, fileName);
                thumbnail.transferTo(savedFile);

                String fullImageUrl = "https://swp391-su25.onrender.com/images/blog-thumbnails/" + fileName;
                blog.setImg(fullImageUrl);

            } catch (IOException e) {
                e.printStackTrace();
                throw new RuntimeException("Lỗi khi lưu ảnh blog", e);
            }
        }

        Account account = accountRepository.findById(accountId)
                .orElseThrow(() -> new RuntimeException("Không tìm thấy tài khoản"));
        blog.setAccount(account);

        return BlogDTO.toDTO(blogRepository.save(blog));
    }







    private String generateUniqueBlogId() {
        String blogId;
        do {
            String time = new SimpleDateFormat("yyyyMMddHHmmss").format(new Date());
            int random = new Random().nextInt(900) + 100;
            blogId = "BL-" + time + "-" + random;
        } while (blogRepository.existsBlogsByBlogId(blogId));
        return blogId;
    }

    @Transactional
    public Map<String, Object> deleteMultipleBlogsSafe(List<String> ids) {
        List<String> deleted = new ArrayList<>();
        Map<String, String> errors = new HashMap<>();

        for (String id : ids) {
            try {
                Blog blog = blogRepository.findBlogByBlogId(id)
                        .orElseThrow(() -> new EntityNotFoundException("Không tìm thấy blog với ID: " + id));
                blogRepository.delete(blog);
                deleted.add(id);
            } catch (EntityNotFoundException e) {
                errors.put(id, "Không tìm thấy blog");
            } catch (Exception e) {
                errors.put(id, "Lỗi không xác định: " + e.getMessage());
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("deleted", deleted);
        result.put("errors", errors);
        return result;
    }





}
